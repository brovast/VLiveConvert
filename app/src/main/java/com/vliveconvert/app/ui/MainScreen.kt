package com.vliveconvert.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.settings.AppSettings
import com.vliveconvert.app.settings.MainViewMode
import com.vliveconvert.glass.GlassBackground
import com.vliveconvert.glass.GlassBottomSheet
import com.vliveconvert.glass.GlassCapsuleToolbar
import com.vliveconvert.glass.GlassDefaults
import com.vliveconvert.glass.GlassIconButton
import com.vliveconvert.glass.GlassQuality
import com.vliveconvert.glass.GlassScaffold
import com.vliveconvert.glass.GlassSurface
import com.vliveconvert.glass.GlassToolbarIcon
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 转换列表中的一项（源照片 + 当前状态） */
data class ConvertItem(
    val item: MediaItem,
    val status: String = "待转换",
    val failed: Boolean = false,
    val done: Boolean = false,
)

/**
 * 主界面：悬浮玻璃顶栏 + 胶囊工具栏 + 左右悬浮玻璃按钮 + 玻璃设置面板。
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
    isMovingOutputs: Boolean,
    onEditOutputPath: () -> Unit,
    onMoveToCamera: () -> Unit,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onAddMore: () -> Unit,
    onStartConvert: () -> Unit,
    onClearAll: () -> Unit,
    onOpenFixTime: () -> Unit,
    onRemove: (ConvertItem) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    quality: GlassQuality,
    versionName: String,
    totalRamMb: Long
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isGrid = settings.mainViewMode == MainViewMode.Grid
    var showSettings by remember { mutableStateOf(false) }

    // 批次结束后回到列表顶部，让用户看到最终状态（成功/失败统计）
    var wasConverting by remember { mutableStateOf(false) }
    LaunchedEffect(isConverting) {
        if (wasConverting && !isConverting) {
            runCatching {
                if (isGrid) gridState.animateScrollToItem(0)
                else listState.animateScrollToItem(0)
            }
        }
        wasConverting = isConverting
    }

    // 滚动期间暂停玻璃：移除 backdrop 采样层 + 所有 blur/lens，停止后 140ms 恢复
    var scrollPaused by remember { mutableStateOf(false) }
    val isScrolling = if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            scrollPaused = true
        } else {
            delay(140)
            scrollPaused = false
        }
    }
    val glassActive = quality != GlassQuality.Off &&
        !(settings.pauseGlassOnScroll && scrollPaused)

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val insetTop = insets.calculateTopPadding()
    val insetBottom = insets.calculateBottomPadding()
    val insetStart = insets.calculateStartPadding(layoutDirection)
    val insetEnd = insets.calculateEndPadding(layoutDirection)

    val canStart = !isConverting && items.any { !it.done && !it.failed }

    GlassScaffold(
        glassEnabled = glassActive,
        background = {
            GlassBackground(
                Modifier.fillMaxSize(),
                animated = settings.animatedBackground
            )
        },
        content = {
            val contentPadding = PaddingValues(
                start = 16.dp + insetStart,
                end = 16.dp + insetEnd,
                top = insetTop + 148.dp,
                bottom = insetBottom + if (isConverting) 172.dp else 112.dp
            )
            if (!isGrid) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (statusText.isNotEmpty()) {
                        item(key = "status") { StatusCard(statusText) }
                    }
                    if (pendingRestoreCount > 0) {
                        item(key = "restore") {
                            RestoreCard(pendingRestoreCount, onRestoreOriginals)
                        }
                    }
                    if (items.isEmpty()) {
                        item(key = "empty") { EmptyState(Modifier.fillParentMaxHeight(0.68f)) }
                    }
                    listItems(items, key = { it.item.key }) { ci ->
                        ConvertItemRow(ci = ci, onRemove = { onRemove(ci) })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (statusText.isNotEmpty()) {
                        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                            StatusCard(statusText)
                        }
                    }
                    if (pendingRestoreCount > 0) {
                        item(key = "restore", span = { GridItemSpan(maxLineSpan) }) {
                            RestoreCard(pendingRestoreCount, onRestoreOriginals)
                        }
                    }
                    if (items.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Modifier.fillMaxWidth().height(360.dp))
                        }
                    }
                    gridItems(items, key = { it.item.key }) { ci ->
                        ConvertItemGridCard(ci = ci, onRemove = { onRemove(ci) })
                    }
                }
            }
        },
        overlays = { backdrop ->
            // ── 悬浮顶栏 + 胶囊工具栏 ──
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedRectangle(26.dp),
                    quality = quality,
                    enabled = glassActive,
                    interactive = false,
                    surfaceColor = GlassDefaults.strongSurfaceColor(),
                    highlight = Highlight.Default,
                    shadow = Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.10f))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "VLiveConvert",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "vivo 双文件实况 → 单文件实况",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        GlassToolbarIcon(
                            onClick = {
                                onSettingsChange(
                                    settings.copy(
                                        mainViewMode =
                                            if (isGrid) MainViewMode.List
                                            else MainViewMode.Grid
                                    )
                                )
                            },
                            contentDescription =
                                if (isGrid) "切换为列表" else "切换为平铺"
                        ) {
                            Icon(
                                if (isGrid) AppIcons.ViewList else AppIcons.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        GlassToolbarIcon(
                            onClick = { showSettings = true },
                            contentDescription = "设置"
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                GlassCapsuleToolbar(
                    backdrop = backdrop,
                    quality = quality,
                    enabled = glassActive,
                    surfaceColor = GlassDefaults.surfaceColor()
                ) {
                    GlassToolbarIcon(
                        onClick = onOpenFixTime,
                        contentDescription = "修复文件时间",
                        enabled = !isConverting && !isMovingOutputs
                    ) {
                        Icon(
                            AppIcons.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    GlassToolbarIcon(
                        onClick = onMoveToCamera,
                        contentDescription = "移到相机相册",
                        enabled = !isConverting && !isMovingOutputs
                    ) {
                        Icon(
                            AppIcons.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    GlassToolbarIcon(
                        onClick = onClearAll,
                        contentDescription = "清空列表",
                        enabled = items.isNotEmpty() && !isConverting
                    ) {
                        Icon(
                            AppIcons.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── 底部左右悬浮按钮 + 转换进度 ──
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isConverting) {
                    GlassSurface(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedRectangle(22.dp),
                        quality = quality,
                        enabled = glassActive,
                        interactive = false,
                        surfaceColor = GlassDefaults.strongSurfaceColor(),
                        highlight = Highlight.Plain
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    progressDetail.ifEmpty { "正在转换…" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            ProgressBar(progress)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        backdrop = backdrop,
                        onClick = onAddMore,
                        modifier = Modifier.size(64.dp),
                        quality = quality,
                        enabled = !isConverting,
                        interactive = true,
                        surfaceColor = GlassDefaults.surfaceColor(),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "添加照片",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    val startTint =
                        if (canStart || isConverting) Color(0xFF415FFF) else Color(0xFF8A8F9C)
                    GlassIconButton(
                        backdrop = backdrop,
                        onClick = onStartConvert,
                        modifier = Modifier
                            .size(64.dp)
                            .semantics {
                                contentDescription =
                                    if (isConverting) "转换中" else "开始转换"
                            },
                        quality = quality,
                        enabled = canStart || isConverting,
                        interactive = true,
                        tint = startTint,
                        surfaceColor = Color.White.copy(alpha = 0.10f),
                        shape = CircleShape
                    ) {
                        if (isConverting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // ── 设置面板（窗口内浮层，可采样主界面 backdrop） ──
            GlassBottomSheet(
                visible = showSettings,
                onDismiss = { showSettings = false },
                backdrop = backdrop,
                quality = quality
            ) { sheetBackdrop ->
                SettingsSheetContent(
                    sheetBackdrop = sheetBackdrop,
                    settings = settings,
                    quality = quality,
                    totalRamMb = totalRamMb,
                    pendingRestoreCount = pendingRestoreCount,
                    onSettingsChange = onSettingsChange,
                    onEditOutputPath = onEditOutputPath,
                    onToggleDeleteOriginal = onToggleDeleteOriginal,
                    onRestoreOriginals = onRestoreOriginals,
                    onClose = { showSettings = false },
                    versionName = versionName
                )
            }
        }
    )
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(Capsule())
            .background(GlassDefaults.trackColor())
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(Capsule())
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF7C8CFF), Color(0xFF415FFF))
                    )
                )
        )
    }
}

@Composable
private fun StatusCard(text: String) {
    CardSurface {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RestoreCard(count: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    CardSurface(
        modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "恢复已删除的原图（$count 项，30 天内有效）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            AppIcons.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "还没有选择照片",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "点击左下角「添加照片」，从相册中选择双文件实况照片\n" +
                "（vivo 相机实况模式拍摄的 .jpg + .mp4 成对文件）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dark = GlassDefaults.isDark()
    val cardColor =
        if (dark) Color(0xFF121318).copy(alpha = 0.52f)
        else Color.White.copy(alpha = 0.62f)
    val borderColor =
        if (dark) Color.White.copy(alpha = 0.08f)
        else Color.White.copy(alpha = 0.58f)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedRectangle(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        content()
    }
}

@Composable
private fun ConvertItemRow(ci: ConvertItem, onRemove: () -> Unit) {
    val dark = GlassDefaults.isDark()
    val cardColor =
        if (dark) Color(0xFF121318).copy(alpha = 0.52f)
        else Color.White.copy(alpha = 0.62f)
    val borderColor =
        if (dark) Color.White.copy(alpha = 0.08f)
        else Color.White.copy(alpha = 0.58f)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(20.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedRectangle(20.dp))
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedRectangle(12.dp))
            ) {
                MediaThumbnail(
                    ci.item.uri,
                    androidx.compose.ui.platform.LocalContext.current.contentResolver,
                    Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ci.item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
            if (ci.status != "转换中…") {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 平铺模式下的照片卡片：方形缩略图 + 文件名/状态。 */
@Composable
private fun ConvertItemGridCard(ci: ConvertItem, onRemove: () -> Unit) {
    val dark = GlassDefaults.isDark()
    val cardColor =
        if (dark) Color(0xFF121318).copy(alpha = 0.52f)
        else Color.White.copy(alpha = 0.62f)
    val borderColor =
        if (dark) Color.White.copy(alpha = 0.08f)
        else Color.White.copy(alpha = 0.58f)
    val resolver = LocalContext.current.contentResolver

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedRectangle(16.dp))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedRectangle(14.dp))
            ) {
                MediaThumbnail(ci.item.uri, resolver, Modifier.fillMaxSize())
                if (ci.status != "转换中…") {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onRemove
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    ci.item.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    ci.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        ci.failed -> MaterialTheme.colorScheme.error
                        ci.done -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
