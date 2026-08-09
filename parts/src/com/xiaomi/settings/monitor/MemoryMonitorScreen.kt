/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.monitor

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.MonitorBar
import com.xiaomi.settings.ui.PageGutter
import com.xiaomi.settings.ui.SectionHeader
import com.xiaomi.settings.ui.StatCard
import com.xiaomi.settings.ui.groupedCardShape
import com.xiaomi.settings.utils.PartsToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryMonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(ActivityManager::class.java) }

    var memInfo by remember { mutableStateOf<MemInfo?>(null) }
    var processes by remember { mutableStateOf<List<ProcessSample>>(emptyList()) }

    val sampler = remember { ProcessSampler(context) }

    LaunchedEffect(Unit) {
        while (true) {
            val (mem, procs) = withContext(Dispatchers.IO) {
                readMemInfo() to sampler.sample()
            }
            memInfo   = mem
            processes = procs
            delay(2_000)
        }
    }

    val ownUid = android.os.Process.myUid()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.memory_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding() + 8.dp),
        ) {
            RamOverviewCard(
                memInfo = memInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter)
                    .padding(bottom = 8.dp),
            )
            if (memInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.memory_total),
                        value = formatBytes(memInfo!!.totalKb),
                        icon  = Icons.Filled.Memory,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.memory_available),
                        value = formatBytes(memInfo!!.availableKb),
                        icon  = Icons.Filled.Speed,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = stringResource(R.string.memory_cached),
                        value = formatBytes(memInfo!!.cachedKb),
                        icon  = Icons.Filled.SdStorage,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.memory_swap),
                        value = if (memInfo!!.swapTotalKb > 0) {
                            formatBytes(memInfo!!.swapFreeKb)
                        } else {
                            "—"
                        },
                        icon  = Icons.Filled.SwapVert,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            LazyColumn(
                modifier       = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                ),
            ) {
                item(key = "procs-label") { SectionHeader(stringResource(R.string.memory_processes)) }
                if (processes.isEmpty()) {
                    item(key = "procs-empty") {
                        Text(
                            text     = stringResource(R.string.memory_no_processes),
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                } else {
                    items(
                        items = processes,
                        key   = { it.pid },
                    ) { proc ->
                        val index = processes.indexOf(proc)
                        ProcessRow(
                            proc    = proc,
                            killable = proc.uid != ownUid && proc.uid != 0 &&
                                !proc.name.startsWith("com.xiaomi.settings"),
                            shape   = groupedCardShape(index, processes.size),
                            onKill = {
                                val pkg = proc.name.substringBefore(':')
                                runCatching { am.killBackgroundProcesses(pkg) }
                                PartsToast.show(context, R.string.memory_process_stopped)
                            },
                            modifier = Modifier
                                .padding(horizontal = PageGutter)
                                .padding(bottom = if (index == processes.lastIndex) 0.dp else 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamOverviewCard(
    memInfo:  MemInfo?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (memInfo == null) {
                Text(
                    text  = stringResource(R.string.memory_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Card
            }
            MonitorBar(
                label   = stringResource(R.string.memory_used),
                valueText = "${formatBytes(memInfo.usedKb)} / ${formatBytes(memInfo.totalKb)}",
                fraction = memInfo.usedKb.toFloat() / memInfo.totalKb.toFloat(),
                secondaryText = stringResource(
                    R.string.memory_free_summary,
                    formatBytes(memInfo.freeKb),
                ),
                color = when {
                    memInfo.usedKb > memInfo.totalKb * 0.9 -> MaterialTheme.colorScheme.error
                    memInfo.usedKb > memInfo.totalKb * 0.7 -> MaterialTheme.colorScheme.tertiary
                    else                                    -> MaterialTheme.colorScheme.primary
                },
            )
            if (memInfo.usedKb > memInfo.totalKb * 0.9) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(20.dp),
                    )
                    Text(
                        text  = stringResource(R.string.memory_low_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    proc:     ProcessSample,
    killable: Boolean,
    shape:    androidx.compose.ui.graphics.Shape,
    onKill:   () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = appIcon(proc.name.substringBefore(':'))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = shape,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier              = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (icon != null) {
                    Image(
                        bitmap             = icon,
                        contentDescription = null,
                        modifier           = Modifier.size(36.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Apps,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text     = proc.name,
                        style    = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = "PSS ${formatBytes(proc.pssKb)} · RSS ${formatBytes(proc.rssKb)}" +
                            " · CPU ${"%.1f".format(proc.cpuPercent)}%",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (killable) {
                IconButton(onClick = onKill) {
                    Icon(
                        imageVector        = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.memory_stop),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun appIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
