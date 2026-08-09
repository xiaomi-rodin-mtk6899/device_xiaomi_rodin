/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.ui.IconTile
import com.xiaomi.settings.ui.PressableCard
import com.xiaomi.settings.utils.CitLauncher
import com.xiaomi.settings.utils.PartsToast

private val topCardShape: Shape
    @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
        bottomEnd   = MaterialTheme.shapes.extraSmall.bottomEnd,
    )

private val bottomCardShape: Shape
    @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraLarge.copy(
        topStart = MaterialTheme.shapes.extraSmall.topStart,
        topEnd   = MaterialTheme.shapes.extraSmall.topEnd,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiaomiPartsHomeScreen(
    onNavigateToDisplay:      () -> Unit,
    onNavigateToResolution:   () -> Unit,
    onNavigateToThermal:      () -> Unit,
    onNavigateToTouch:        () -> Unit,
    onNavigateToBattery:      () -> Unit,
    onNavigateToPerformance:  () -> Unit,
    onNavigateToMemory:       () -> Unit,
) {
    val context          = LocalContext.current
    val horizontalGutter = 20.dp
    val scrollBehavior   = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.xiaomi_parts_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "display-label") { PartsCategory(stringResource(R.string.display_category)) }
            item(key = "display-card-1") {
                HomeCard(
                    icon     = ImageVector.vectorResource(R.drawable.ic_display_colours),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.display_colours_title),
                    summary  = stringResource(R.string.display_colours_summary),
                    shape    = topCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToDisplay,
                )
                Spacer(Modifier.height(2.dp))
            }
            item(key = "display-card-2") {
                HomeCard(
                    icon     = ImageVector.vectorResource(R.drawable.ic_screen_resolution),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.screen_resolution_title),
                    summary  = stringResource(R.string.screen_resolution_home_summary),
                    shape    = bottomCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToResolution,
                )
            }

            item(key = "perf-label") { PartsCategory(stringResource(R.string.performance_category)) }
            item(key = "perf-card-1") {
                HomeCard(
                    icon     = ImageVector.vectorResource(R.drawable.ic_thermal_settings),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.thermal_title),
                    summary  = stringResource(R.string.thermal_summary),
                    shape    = topCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToThermal,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            item(key = "perf-card-2") {
                HomeCard(
                    icon     = ImageVector.vectorResource(R.drawable.ic_touch_boost),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.touch_boost_title),
                    summary  = stringResource(R.string.touch_boost_summary),
                    shape    = bottomCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToTouch,
                )
            }
            item(key = "system-label") { PartsCategory(stringResource(R.string.system_category)) }
            item(key = "system-card-1") {
                HomeCard(
                    icon     = Icons.Filled.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.battery_status_title),
                    summary  = stringResource(R.string.battery_status_summary),
                    shape    = topCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToBattery,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            item(key = "system-card-2") {
                HomeCard(
                    icon     = Icons.Filled.Speed,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.performance_title),
                    summary  = stringResource(R.string.performance_summary),
                    shape    = MaterialTheme.shapes.extraSmall,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToPerformance,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            item(key = "system-card-3") {
                HomeCard(
                    icon     = Icons.Filled.Memory,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.memory_title),
                    summary  = stringResource(R.string.memory_summary),
                    shape    = bottomCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = onNavigateToMemory,
                )
            }

            item(key = "diag-label") { PartsCategory(stringResource(R.string.xiaomi_parts_category_diagnostics)) }
            item(key = "diag-card-1") {
                HomeCard(
                    icon     = Icons.Filled.Fingerprint,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.fingerprint_calibration_title),
                    summary  = stringResource(R.string.fingerprint_calibration_summary),
                    shape    = topCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = {
                        if (!CitLauncher.launchFingerprintCalibration(context)) {
                            PartsToast.show(context, R.string.fingerprint_calibration_not_found)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            item(key = "diag-card-2") {
                HomeCard(
                    icon     = Icons.Filled.Speaker,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title    = stringResource(R.string.speaker_calibration_title),
                    summary  = stringResource(R.string.speaker_calibration_summary),
                    shape    = bottomCardShape,
                    horizontalGutter = horizontalGutter,
                    onClick  = {
                        if (!CitLauncher.launchSpeakerCalibration(context)) {
                            PartsToast.show(context, R.string.speaker_calibration_not_found)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun PartsCategory(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 36.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun HomeCard(
    icon:              ImageVector,
    iconTint:          Color,
    title:             String,
    summary:           String,
    shape:             Shape,
    horizontalGutter:  androidx.compose.ui.unit.Dp,
    onClick:           () -> Unit,
) {
    PressableCard(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalGutter),
        shape    = shape,
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(
                icon      = icon,
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                tint      = iconTint,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = summary,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}
