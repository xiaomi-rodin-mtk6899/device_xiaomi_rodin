/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: The XPerience Project
 */

#define LOG_TAG "UdfpsHandler.Rodin"

#include <aidl/android/hardware/biometrics/fingerprint/BnFingerprint.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>

#include <atomic>
#include <poll.h>
#include <sys/ioctl.h>
#include <fstream>
#include <thread>
#include <bitset>

#include "mi_disp.h"

#include "UdfpsHandler.h"

#define CMD_DATA_BUF_SIZE 256

#define COMMON_DATA_CMD 0
#define SELECT_TOUCH_ID 3
#define SET_CUR_VALUE 0

#define Touch_Fod_Enable 10
#define THP_FOD_DOWNUP_CTL 1001

#define COMMAND_NIT 10
#define PARAM_NIT_FOD 1
#define PARAM_NIT_NONE 0

#define COMMAND_FOD_PRESS_STATUS 1
#define PARAM_FOD_PRESSED 1
#define PARAM_FOD_RELEASED 0

#define FOD_STATUS_OFF 0
#define FOD_STATUS_ON 1

#define TOUCH_DEV_PATH "/dev/xiaomi-touch"
#define TOUCH_MAGIC 0x54

#define DISP_FEATURE_PATH "/dev/mi_display/disp_feature"

#define FOD_PRESS_STATUS_PATH "/sys/class/touch/touch_dev/fod_press_status"

typedef struct {
    int8_t touch_id;
    uint8_t cmd;
    uint16_t mode;
    uint16_t data_len;
    int32_t data_buf[CMD_DATA_BUF_SIZE];
} touch_base;

#define TOUCH_IOC_SELECT_TOUCH_ID _IOW(TOUCH_MAGIC, SELECT_TOUCH_ID, int)
#define TOUCH_IOC_COMMON_DATA _IOW(TOUCH_MAGIC, COMMON_DATA_CMD, touch_base)

using ::aidl::android::hardware::biometrics::fingerprint::AcquiredInfo;

namespace {

static bool readBool(int fd) {
    char c;
    int rc;

    rc = lseek(fd, 0, SEEK_SET);
    if (rc) {
        LOG(ERROR) << "failed to seek fd, err: " << rc;
        return false;
    }

    rc = read(fd, &c, sizeof(char));
    if (rc != 1) {
        LOG(ERROR) << "failed to read bool from fd, err: " << rc;
        return false;
    }

    return c != '0';
}

static disp_event_resp* parseDispEvent(int fd) {
    static char event_data[1024] = {0};
    ssize_t size;

    memset(event_data, 0x0, sizeof(event_data));
    size = read(fd, event_data, sizeof(event_data));
    if (size < 0) {
        LOG(ERROR) << "read fod event failed";
        return nullptr;
    }

    if (size < sizeof(struct disp_event)) {
        LOG(ERROR) << "Invalid event size " << size << ", expect at least "
                   << sizeof(struct disp_event);
        return nullptr;
    }

    return (struct disp_event_resp*)&event_data[0];
}

struct disp_base displayBasePrimary = {
        .flag = 0,
        .disp_id = MI_DISP_PRIMARY,
};

touch_base touchDataPrimary = {
        .touch_id = MI_DISP_PRIMARY,
        .cmd = SET_CUR_VALUE,
        .mode = 0,
        .data_len = 1,
        .data_buf = {},
};

}  // anonymous namespace

class XiaomiRodinUdfpsHandler : public UdfpsHandler {
  public:
    XiaomiRodinUdfpsHandler() : mRunning(true) {}

    void init(fingerprint_device_t* device) override {
        mDevice = device;
        touch_fd_ = android::base::unique_fd(open(TOUCH_DEV_PATH, O_RDWR));
        disp_fd_ = android::base::unique_fd(open(DISP_FEATURE_PATH, O_RDWR));

        // Thread to notify fingerprint hwmodule about FOD press/release events.
        // mFingerPressed is the single source of truth for physical finger state —
        // only this thread writes to it, avoiding races with setFingerDown().
        std::thread([this]() {
            android::base::unique_fd fd(open(FOD_PRESS_STATUS_PATH, O_RDONLY));
            if (fd < 0) {
                LOG(ERROR) << "failed to open " << FOD_PRESS_STATUS_PATH << " , err: " << fd.get();
                return;
            }

            struct pollfd fodPressStatusPoll = {
                    .fd = fd.get(),
                    .events = POLLERR | POLLPRI,
                    .revents = 0,
            };

            while (mRunning.load()) {
                int rc = poll(&fodPressStatusPoll, 1, -1);
                if (rc < 0) {
                    LOG(ERROR) << "failed to poll " << FOD_PRESS_STATUS_PATH << ", err: " << rc;
                    continue;
                }

                bool pressed = readBool(fd.get());
                mFingerPressed.store(pressed);
                mDevice->extCmd(mDevice, COMMAND_FOD_PRESS_STATUS,
                                pressed ? PARAM_FOD_PRESSED : PARAM_FOD_RELEASED);
                // Reflect the new press state into HBM (only lights up if FOD UI is also active)
                updateHbm();
            }
        }).detach();

        // Thread to listen for FOD UI events from the display driver
        std::thread([this]() {
            android::base::unique_fd fd(open(DISP_FEATURE_PATH, O_RDWR));
            if (fd < 0) {
                LOG(ERROR) << "failed to open " << DISP_FEATURE_PATH << " , err: " << fd.get();
                return;
            }

            // Register for FOD events
            struct disp_event_req displayEventRequest = {
                    .base = displayBasePrimary,
                    .type = MI_DISP_EVENT_FOD,
            };
            if (ioctl(fd.get(), MI_DISP_IOCTL_REGISTER_EVENT, &displayEventRequest) < 0) {
                LOG(ERROR) << "failed to register FOD event";
                return;
            }

            struct pollfd dispEventPoll = {
                    .fd = fd.get(),
                    .events = POLLIN,
                    .revents = 0,
            };

            while (mRunning.load()) {
                int rc = poll(&dispEventPoll, 1, -1);
                if (rc < 0) {
                    LOG(ERROR) << "failed to poll " << DISP_FEATURE_PATH << ", err: " << rc;
                    continue;
                }

                struct disp_event_resp* response = parseDispEvent(fd.get());
                if (response == nullptr) {
                    continue;
                }

                if (response->base.type != MI_DISP_EVENT_FOD) {
                    LOG(ERROR) << "unexpected display event: " << response->base.type;
                    continue;
                }

                handleDisplayEvent(response->data[0]);
            }
        }).detach();
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        if (mAuthCompleted.load()) return;
        LOG(INFO) << __func__;
        // Ensure touchscreen is aware of the press state; ideally not needed
        setFingerDown(true);
    }

    void onFingerUp() override {
        LOG(INFO) << __func__;
        // Ensure touchscreen is aware of the release state; ideally not needed
        setFingerDown(false);
    }

    void onAcquired(int32_t result, int32_t vendorCode) override {
        LOG(INFO) << __func__ << " result: " << result << " vendorCode: " << vendorCode;

        /* vendorCode
         * 21: waiting for finger (FOD UI ready)
         * 22: finger down
         * 23: finger up
         */
        if (vendorCode == 21) {
            mFodEnabled.store(true);
            setFodStatus(FOD_STATUS_ON);
            // If the finger is already physically pressed, light up HBM immediately
            updateHbm();
        }
    }
    
    void cancel() override {
        LOG(INFO) << __func__;
        resetFodState();
    }

    void onAuthenticationSucceeded() {
        LOG(INFO) << __func__;
        mAuthCompleted.store(true);
        // Disable FOD UI and HBM immediately on success
        mFodEnabled.store(false);
        setFodStatus(FOD_STATUS_OFF);
        updateHbm();

        // Give the display a short window to render the success animation
        // before releasing the finger down state
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(300));
            setFingerDown(false);
            mAuthCompleted.store(false);
        }).detach();
    }

    void onAuthenticationFailed() {
        LOG(INFO) << __func__;
        // Full reset — a failed attempt must go through vendorCode 21 again
        // before HBM can be re-enabled
        mAuthCompleted.store(false);
        updateHbm();
    }

  private:
    fingerprint_device_t* mDevice = nullptr;
    android::base::unique_fd touch_fd_;
    android::base::unique_fd disp_fd_;
    std::atomic<bool> mAuthCompleted{false};
    std::atomic<bool> mFodEnabled{false};    // True when the FOD UI overlay is active
    std::atomic<bool> mFingerPressed{false}; // True when the finger is physically on the sensor
                                             // Written only by the FOD press status thread

    // Centralized HBM control — HBM is only active when both the FOD UI
    // is visible AND the finger is physically pressing the sensor
    void updateHbm() {
        __u32 hbm_value = (mFodEnabled.load() && mFingerPressed.load())
                            ? LHBM_TARGET_BRIGHTNESS_WHITE_1000NIT
                            : LHBM_TARGET_BRIGHTNESS_OFF_FINGER_UP;
        struct disp_local_hbm_req req = {
            .base = displayBasePrimary,
            .local_hbm_value = hbm_value,
        };
        ioctl(disp_fd_.get(), MI_DISP_IOCTL_SET_LOCAL_HBM, &req);
    }

    // Resets all FOD state. Used by cancel() and onAuthenticationFailed()
    // to ensure a clean slate before the next authentication attempt.
    void resetFodState() {
        mFodEnabled.store(false);
        mAuthCompleted.store(false);
        setFodStatus(FOD_STATUS_OFF);
        updateHbm();
    }

    void setFodStatus(int value) {
        if (touch_fd_.get() < 0) {
            LOG(ERROR) << "touch_fd_ invalid";
            return;
        }
        ioctl(touch_fd_.get(), TOUCH_IOC_SELECT_TOUCH_ID, MI_DISP_PRIMARY);
        touch_base data = {
                    .mode = Touch_Fod_Enable,
                    .data_buf = {value},
        };
        ioctl(touch_fd_.get(), TOUCH_IOC_COMMON_DATA, &data);
    }

    // Notifies the touchscreen of finger press/release state.
    // Does NOT write mFingerPressed — that is exclusively owned by the
    // FOD press status polling thread to avoid races.
    void setFingerDown(bool pressed) {
        if (touch_fd_.get() < 0) {
            LOG(ERROR) << "touch_fd_ invalid";
            return;
        }
        ioctl(touch_fd_.get(), TOUCH_IOC_SELECT_TOUCH_ID, MI_DISP_PRIMARY);
        touch_base data = {
                    .mode = THP_FOD_DOWNUP_CTL,
                    .data_buf = {pressed ? 1 : 0},
        };
        ioctl(touch_fd_.get(), TOUCH_IOC_COMMON_DATA, &data);
    }

    void handleDisplayEvent(int value) {
        bool uiReady = value & LOCAL_HBM_UI_READY;

        if (uiReady && mAuthCompleted.load()) {
            // Authentication already completed — ignore stale FOD UI events from the driver
            mDevice->extCmd(mDevice, COMMAND_NIT, PARAM_NIT_NONE);
            return;
        }

        if (mDevice) {
            mDevice->extCmd(mDevice, COMMAND_NIT, uiReady ? PARAM_NIT_FOD : PARAM_NIT_NONE);
        }
    }
};

static UdfpsHandler* create() {
    return new XiaomiRodinUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
