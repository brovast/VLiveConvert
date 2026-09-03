package com.vliveconvert.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.picker.MediaItem

/** 转换列表中的一项（源照片 + 当前状态） */
data class ConvertItem(
    val item: MediaItem,
    val status: String = "待转换",
    val failed: Boolean = false,
    val done: Boolean = false,
)

/**
 * 主界面：待转换列表 + 开始转换。
 */
@Composable
fun MainScreen(
    items: List<ConvertItem>,
    statusText: String,
    isConverting: Boolean,
    progress: Float,
    progressDetail: String,
    pendingRestoreCount: Int,
    onRestoreOriginals: () -> Unit,
    outputRelPath: String,
    isMovingOutputs: Boolean,
    onEditOutputPath: () -> Unit,
    onMoveToCamera: () -> Unit,
    deleteOriginal: Boolean,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onAddMore: () -> Unit,
    onStartConvert: () -> Unit,
    onClearAll: () -> Unit,
    onOpenFixTime: () -> Unit,
    onRemove: (ConvertItem) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶栏 ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("VLiveConvert", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("vivo 双文件实况 → 单文件实况",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isConverting) {
                        Text(
                            "修复时间",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickableNoRipple(onOpenFixTime)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (!isConverting && !isMovingOutputs) {
                        Text(
                            "移到相机",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickableNoRipple(onMoveToCamera)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (items.isNotEmpty() && !isConverting) {
                        Text(
                            "清空",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickableNoRipple(onClearAll)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // ── 状态条 ──
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ── 恢复原图入口（回收站路径删除后显示，30 天内有效） ──
        if (pendingRestoreCount > 0) {
            Text(
                "↩ 恢复已删除的原图（${pendingRestoreCount} 项，30 天内有效）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoRipple(onRestoreOriginals)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // ── 列表 ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有选择照片", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击下方「添加照片」，从相册中选择双文件实况照片\n（vivo 相机实况模式拍摄的 .jpg + .mp4 成对文件）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.item.key }) { ci ->
                        ConvertItemRow(ci = ci, onRemove = { onRemove(ci) })
                    }
                }
            }
        }

        // ── 底部操作栏 ──
        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isConverting) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    Text(
                        progressDetail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                // ── 输出目录（点击修改） ──
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "输出目录：$outputRelPath",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickableNoRipple(onEditOutputPath)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        "修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickableNoRipple(onEditOutputPath)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                // ── 转换后删除原图开关（处理中禁用，保证批次语义确定） ──
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("转换后删除原图",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "删除原 .jpg 与伴生 .mp4（恢复方式见删除完成后的提示）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = deleteOriginal,
                        onCheckedChange = onToggleDeleteOriginal,
                        enabled = !isConverting
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onAddMore,
                        enabled = !isConverting,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加照片")
                    }
                    Button(
                        onClick = onStartConvert,
                        enabled = !isConverting && items.any { !it.done && !it.failed },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text(if (isConverting) "转换中…" else "开始转换")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertItemRow(ci: ConvertItem, onRemove: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                MediaThumbnail(ci.item.uri, resolver, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ci.item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    ci.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        ci.failed -> MaterialTheme.colorScheme.error
                        ci.done -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (!isBusyStatus(ci.status)) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 转换中的项目不显示移除按钮 */
private fun isBusyStatus(status: String): Boolean = status == "转换中…"

/** 无涟漪点击（文本按钮用） */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
