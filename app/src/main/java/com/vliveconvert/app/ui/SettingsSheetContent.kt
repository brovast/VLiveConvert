package com.vliveconvert.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.vliveconvert.app.settings.AppSettings
import com.vliveconvert.app.settings.GlassQualityOption
import com.vliveconvert.app.settings.MainViewMode
import com.vliveconvert.app.settings.SettingsPreset
import com.vliveconvert.app.settings.withPreset
import com.vliveconvert.glass.GlassDefaults
import com.vliveconvert.glass.GlassPillButton
import com.vliveconvert.glass.GlassQuality
import com.vliveconvert.glass.GlassSegmentedControl
import com.vliveconvert.glass.GlassSlider
import com.vliveconvert.glass.GlassToggle
import kotlin.math.roundToInt

/**
 * 设置面板内容（放在 [com.vliveconvert.glass.GlassBottomSheet] 内）。
 *
 * 所有性能参数即时保存；转换中的批次使用开始时的参数，下一批生效。
 */
@Composable
fun SettingsSheetContent(
    sheetBackdrop: Backdrop,
    settings: AppSettings,
    quality: GlassQuality,
    totalRamMb: Long,
    pendingRestoreCount: Int,
    onSettingsChange: (AppSettings) -> Unit,
    onEditOutputPath: () -> Unit,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onRestoreOriginals: () -> Unit,
    onClose: () -> Unit,
    versionName: String
) {
    // 玻璃画质只影响渲染，不影响控件可交互性（否则关闭玻璃后无法再打开）
    val controlsEnabled = true
    val presets = listOf(
        SettingsPreset.PowerSaver,
        SettingsPreset.Balanced,
        SettingsPreset.Performance,
        SettingsPreset.Custom
    )

    fun update(block: (AppSettings) -> AppSettings) {
        onSettingsChange(block(settings).copy(preset = SettingsPreset.Custom))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // ── 标题 ──
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "性能、玻璃特效、界面与输出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 预设 ──
        SectionTitle("性能预设")
        GlassSegmentedControl(
            options = presets.map { it.label },
            selectedIndex = presets.indexOf(settings.preset).coerceAtLeast(0),
            onSelect = { index ->
                val preset = presets[index]
                if (preset == SettingsPreset.Custom) {
                    onSettingsChange(settings.copy(preset = SettingsPreset.Custom))
                } else {
                    onSettingsChange(settings.withPreset(preset))
                }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "预设只影响性能参数，不会改动输出目录与删除原图开关。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── 玻璃特效 ──
        SectionTitle("玻璃特效")
        Text(
            "玻璃特效：${settings.glassQuality.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassSegmentedControl(
            options = GlassQualityOption.entries.map { it.label },
            selectedIndex = GlassQualityOption.entries.indexOf(settings.glassQuality),
            onSelect = { index ->
                update { it.copy(glassQuality = GlassQualityOption.entries[index]) }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            title = "滑动时关闭玻璃",
            subtitle = "滚动/惯性期间暂停模糊与折射，优先保证跟手",
            checked = settings.pauseGlassOnScroll,
            onCheckedChange = { on -> update { it.copy(pauseGlassOnScroll = on) } },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )
        SwitchRow(
            title = "动态背景",
            subtitle = "背景柔光缓慢移动；关闭更省电",
            checked = settings.animatedBackground,
            onCheckedChange = { on -> update { it.copy(animatedBackground = on) } },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        // ── 界面 ──
        SectionTitle("界面")
        Text(
            "主界面布局",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassSegmentedControl(
            options = MainViewMode.entries.map { it.label },
            selectedIndex = MainViewMode.entries.indexOf(settings.mainViewMode),
            onSelect = { index ->
                onSettingsChange(settings.copy(mainViewMode = MainViewMode.entries[index]))
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        // ── 转换性能 ──
        SectionTitle("转换性能")
        Text(
            "转换中的批次使用开始时的参数，修改将在下一批生效。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SliderRow(
            title = "转换并发数",
            valueText = "${settings.concurrency} 张",
            description = "同时处理的照片数量，即 CPU 工作线程数。越高越快，内存占用越大；" +
                "三百张以上建议 2–3。",
            value = settings.concurrency.toFloat(),
            valueRange = 1f..AppSettings.maxConcurrency().toFloat(),
            steps = (AppSettings.maxConcurrency() - 1).coerceAtLeast(1),
            onValueChange = { raw ->
                update { it.copy(concurrency = raw.roundToInt().coerceIn(1, AppSettings.maxConcurrency())) }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        val memoryUpper = AppSettings.memoryUpperBoundMb()
        SliderRow(
            title = "内存上限",
            valueText = "${settings.memoryLimitMb} MB",
            description = "单批转换可用的内存预算；超出后自动排队等待，避免内存不足闪退。",
            value = settings.memoryLimitMb.toFloat(),
            valueRange = 128f..memoryUpper.toFloat(),
            steps = ((memoryUpper - 128) / 16).coerceAtLeast(1),
            onValueChange = { raw ->
                update { it.copy(memoryLimitMb = (raw.roundToInt() / 16 * 16).coerceIn(128, memoryUpper)) }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        SliderRow(
            title = "相册扫描线程",
            valueText = "${settings.scanThreads} 线程",
            description = "识别双文件实况时的并行读取数量。相册很大时可适当降低。",
            value = settings.scanThreads.toFloat(),
            valueRange = 1f..8f,
            steps = 7,
            onValueChange = { raw ->
                update { it.copy(scanThreads = raw.roundToInt().coerceIn(1, 8)) }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        SliderRow(
            title = "缩略图解码并发",
            valueText = "${settings.thumbnailThreads} 线程",
            description = "列表缩略图同时加载数量，影响滚动流畅度。",
            value = settings.thumbnailThreads.toFloat(),
            valueRange = 1f..8f,
            steps = 7,
            onValueChange = { raw ->
                update { it.copy(thumbnailThreads = raw.roundToInt().coerceIn(1, 8)) }
            },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        SwitchRow(
            title = "转换时保持屏幕常亮",
            subtitle = "避免批量转换过程中熄屏/被系统冻结",
            checked = settings.keepScreenOn,
            onCheckedChange = { on -> update { it.copy(keepScreenOn = on) } },
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        Divider()

        // ── 输出 ──
        SectionTitle("输出")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEditOutputPath
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "输出目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    settings.outputRelPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "修改",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SwitchRow(
            title = "转换后删除原图",
            subtitle = "删除原 .jpg 与伴生 .mp4；删除后可按提示恢复",
            checked = settings.deleteOriginal,
            onCheckedChange = onToggleDeleteOriginal,
            backdrop = sheetBackdrop,
            quality = quality,
            enabled = controlsEnabled
        )

        if (pendingRestoreCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRestoreOriginals
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    AppIcons.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "恢复已删除的原图（$pendingRestoreCount 项，30 天内有效）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Divider()

        // ── 设备信息 ──
        SectionTitle("设备信息")
        Text(
            "CPU ${AppSettings.cpuCores()} 核 · 应用堆上限 ${AppSettings.maxHeapMb()} MB · " +
                "系统内存 $totalRamMb MB",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Android 不允许把线程绑定到指定 CPU 核心；这里的并发数就是实际参与转换的工作线程数。" +
                "转换本身只用 CPU，GPU 仅用于玻璃渲染，可通过上方「玻璃特效」档位控制。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        GlassPillButton(
            backdrop = sheetBackdrop,
            onClick = {
                onSettingsChange(
                    AppSettings()
                        .copy(
                            deleteOriginal = settings.deleteOriginal,
                            outputRelPath = settings.outputRelPath
                        )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            quality = quality,
            enabled = controlsEnabled,
            surfaceColor = GlassDefaults.surfaceColor()
        ) {
            Text(
                "恢复默认设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VLiveConvert $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "酷安-毛血旺o",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    quality: GlassQuality,
    enabled: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            backdrop = backdrop,
            quality = quality,
            enabled = enabled
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop,
    quality: GlassQuality,
    enabled: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            backdrop = backdrop,
            quality = quality,
            enabled = enabled,
            steps = steps
        )
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
