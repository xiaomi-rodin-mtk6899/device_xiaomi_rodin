/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.PageGutter
import com.xiaomi.settings.ui.SectionHeader
import com.xiaomi.settings.ui.StatCard
import java.io.File
import kotlinx.coroutines.delay

private fun readSysfsLong(path: String): Long? = runCatching {
    File(path).readText().trim().toLongOrNull()
}.getOrNull()

private data class BatterySnapshot(
    val level:       Int,
    val status:      Int,
    val health:      Int,
    val plugged:     Int,
    val tempTenths:  Int,
    val technology:  String,
)

private fun readSnapshot(context: Context): BatterySnapshot {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return BatterySnapshot(
        level      = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        status     = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
        health     = sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
        plugged    = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
        tempTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
        technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var snap by remember { mutableStateOf(readSnapshot(context)) }
    var chargeCounterUah by remember { mutableLongStateOf(0L) }

    val bm = remember { context.getSystemService(BatteryManager::class.java) }

    fun refreshProperties() {
        chargeCounterUah = runCatching { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }
            .getOrDefault(readSysfsLong("/sys/class/power_supply/battery/charge_counter") ?: 0L)
    }

    LaunchedEffect(Unit) {
        refreshProperties()
        while (true) {
            delay(2_000)
            snap = readSnapshot(context)
            refreshProperties()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                snap = readSnapshot(ctx)
                refreshProperties()
            }
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    val statusText = stringResource(statusLabelRes(snap.status))
    val isCharging = snap.plugged != 0
    val level      = snap.level.coerceIn(0, 100)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.battery_status_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "hero") {
                BatteryHeroCard(
                    level       = level,
                    statusText  = statusText,
                    isCharging  = isCharging,
                    tempC       = snap.tempTenths / 10f,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                )
            }

            item(key = "battery-label") { SectionHeader(stringResource(R.string.battery_live_section)) }
            item(key = "battery-grid-1") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.battery_status),
                        value = statusText,
                        icon  = if (isCharging) Icons.Filled.Bolt else Icons.Filled.BatteryFull,
                        tint  = if (isCharging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.battery_plug_type),
                        value = stringResource(plugLabelRes(snap.plugged)),
                        icon  = Icons.Filled.Cable,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "battery-grid-2") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.battery_temperature),
                        value = "%.1f °C".format(snap.tempTenths / 10f),
                        icon  = Icons.Filled.DeviceThermostat,
                        tint  = tempColor(snap.tempTenths / 10f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "battery-grid-4") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.battery_charge_counter),
                        value = if (chargeCounterUah > 0) "%.0f mAh".format(chargeCounterUah / 1000.0) else "—",
                        icon  = Icons.Filled.Memory,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.battery_technology),
                        value = snap.technology,
                        icon  = Icons.Filled.Info,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "wear-label") { SectionHeader(stringResource(R.string.battery_wear_section)) }
            item(key = "wear-grid-1") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.battery_health),
                        value = stringResource(healthLabelRes(snap.health)),
                        icon  = when (snap.health) {
                            BatteryManager.BATTERY_HEALTH_GOOD -> Icons.Filled.CheckCircle
                            BatteryManager.BATTERY_HEALTH_OVERHEAT,
                            BatteryManager.BATTERY_HEALTH_COLD,
                            BatteryManager.BATTERY_HEALTH_DEAD,
                            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
                            -> Icons.Filled.Dangerous
                            else -> Icons.AutoMirrored.Filled.BatteryUnknown
                        },
                        tint  = if (snap.health == BatteryManager.BATTERY_HEALTH_GOOD) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
    }
}
}

@Composable
private fun BatteryHeroCard(
    level:      Int,
    statusText: String,
    isCharging: Boolean,
    tempC:      Float,
    modifier:   Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = Motion.liveValueSpec(),
        label = "batteryLevel",
    )
    val ringColor = when {
        level <= 20 -> MaterialTheme.colorScheme.error
        level <= 40 -> MaterialTheme.colorScheme.tertiary
        else        -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress        = { fraction },
                    modifier        = Modifier.size(128.dp),
                    color           = ringColor,
                    trackColor      = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth     = 14.dp,
                    strokeCap       = StrokeCap.Round,
                )
                AnimatedContent(
                    targetState     = isCharging,
                    transitionSpec  = {
                        fadeIn(animationSpec = Motion.defaultEffectsSpec()) togetherWith
                            fadeOut(animationSpec = Motion.defaultEffectsSpec())
                    },
                    label           = "heroIcon",
                ) { charging ->
                    Icon(
                        imageVector = if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                        contentDescription = null,
                        tint        = ringColor,
                        modifier    = Modifier.size(40.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text  = "$level%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text  = "%.1f °C".format(tempC),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun tempColor(tempC: Float): Color = when {
    tempC < 30f -> MaterialTheme.colorScheme.primary
    tempC < 45f -> MaterialTheme.colorScheme.tertiary
    else        -> MaterialTheme.colorScheme.error
}

private fun statusLabelRes(status: Int): Int = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING     -> R.string.battery_status_charging
    BatteryManager.BATTERY_STATUS_DISCHARGING  -> R.string.battery_status_discharging
    BatteryManager.BATTERY_STATUS_FULL         -> R.string.battery_status_full
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.battery_status_not_charging
    else                                       -> R.string.battery_status_unknown
}

private fun healthLabelRes(health: Int): Int = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD                 -> R.string.battery_health_good
    BatteryManager.BATTERY_HEALTH_OVERHEAT             -> R.string.battery_health_overheat
    BatteryManager.BATTERY_HEALTH_DEAD                 -> R.string.battery_health_dead
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE         -> R.string.battery_health_over_voltage
    BatteryManager.BATTERY_HEALTH_COLD                 -> R.string.battery_health_cold
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE  -> R.string.battery_health_failure
    else                                               -> R.string.battery_status_unknown
}

private fun plugLabelRes(plugged: Int): Int = when {
    plugged and BatteryManager.BATTERY_PLUGGED_AC       != 0 -> R.string.battery_plug_ac
    plugged and BatteryManager.BATTERY_PLUGGED_USB      != 0 -> R.string.battery_plug_usb
    plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> R.string.battery_plug_wireless
    plugged and BatteryManager.BATTERY_PLUGGED_DOCK     != 0 -> R.string.battery_plug_dock
    else                                                      -> R.string.battery_plug_none
}
