package com.vliveconvert.app.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.vliveconvert.app.ui.CircleTriCheckbox
import com.vliveconvert.app.ui.MediaThumbnail
import com.vliveconvert.app.ui.dateKey
import com.vliveconvert.app.ui.dateLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * 内置双文件实况照片选择器。
 *
 * 数据源为 MediaStore 直查 + PickerScanner 会话化多线程扫描，
 * 仅显示「.jpg + 同目录同名 .mp4」的 vivo 双文件实况照片。
 */
@Composable
fun PickerScreen(
    albums: List<AlbumInfo>,
    scanner: PickerScanner,
    scannerScope: CoroutineScope,
    onBack: () -> Unit,
    onConfirm: (List<MediaItem>) -> Unit
) {
    var currentBucketId by remember { mutableStateOf(albums.firstOrNull()?.bucketId) }
    val bucketId = currentBucketId
    val selected = remember { mutableStateListOf<MediaItem>() }

    // 进入/离开相册：进入 diff+续扫，离开取消（保留进度）
    DisposableEffect(bucketId) {
        if (bucketId != null) scanner.enter(bucketId, scannerScope)
        onDispose { if (bucketId != null) scanner.leave(bucketId) }
    }

    val state = remember(bucketId) { if (bucketId != null) scanner.stateOf(bucketId) else null }
    val results = state?.results
    val total = state?.total ?: 0
    // 进度轮询（done/running 为普通并发变量，需轮询驱动重组）
    val progress by produceState(0 to false, state) {
        while (true) {
            value = (state?.doneCount?.get() ?: 0) to (state?.running ?: false)
            delay(200)
        }
    }
    val running = progress.second

    // 网格显示顺序：固定按日期降序
    val n = results?.size ?: 0
    val displayOrder = remember(results, n) {
        results.orEmpty().sortedByDescending { it.sortTime }
    }

    // 单项选择切换（追加到末尾，序号递增）
    val toggleItem: (MediaItem) -> Unit = { item ->
        val idx = selected.indexOfFirst { it.id == item.id }
        if (idx >= 0) selected.removeAt(idx) else selected.add(item)
    }
    // 成组选择切换（日期栏/全选）：组内全选 → 全部取消；否则按传入顺序补齐未选
    val toggleGroup: (List<MediaItem>) -> Unit = { group ->
        val ids = group.map { it.id }.toSet()
        val allIn = group.isNotEmpty() && group.all { g -> selected.any { it.id == g.id } }
        if (allIn) {
            selected.removeAll { it.id in ids }
        } else {
            for (g in group) if (selected.none { it.id == g.id }) selected.add(g)
        }
    }

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
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text("选择双文件实况", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("仅显示 .jpg + .mp4 成对的双文件实况照片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 相册 chip 横滑条
            if (albums.size > 1) {
                LazyRow(
                    Modifier.padding(vertical = 8.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(albums, key = { it.bucketId }) { album ->
                        AlbumChip(
                            album = album,
                            selected = album.bucketId == bucketId,
                            onClick = { currentBucketId = album.bucketId }
                        )
                    }
                }
            }

            // 扫描进度条
            AnimatedVisibility(visible = running) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // ── 工具行：张数（左） + 全选（右） ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (running) "扫描中 ${progress.first}/$total" else "${results?.size ?: 0} 张双文件实况",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            val allSel = n > 0 && displayOrder.all { g -> selected.any { it.id == g.id } }
            val someSel = displayOrder.any { g -> selected.any { it.id == g.id } }
            Text("全选", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            CircleTriCheckbox(
                state = when {
                    allSel -> ToggleableState.On
                    someSel -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
                onClick = { if (n > 0) toggleGroup(displayOrder) }
            )
        }

        // ── 缩略图网格（3 列） ──
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when {
                results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可用相册", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                results.isEmpty() && !running -> Column(
                    Modifier.fillMaxSize().padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("没有找到双文件实况照片", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("拍摄时请保持相机的「实况」开关开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> AlbumGridPage(
                    bucketId = bucketId,
                    scanner = scanner,
                    selected = selected,
                    onToggle = toggleItem,
                    onToggleGroup = toggleGroup,
                    running = running
                )
            }
        }

        // ── 底部确认栏 ──
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selected.isEmpty()) "" else "已选 ${selected.size} 张",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty()
                ) { Text("添加") }
            }
        }
    }
}

/**
 * 单个相册的网格页：固定按日期降序分组显示。
 * 日期分组 + 粘性标题；必须先排序取快照再 groupBy 归组逐组发射
 * （扫描期间流式追加未排序，直接遍历发射会导致 stickyHeader 重复 key 崩溃）。
 */
@Composable
private fun AlbumGridPage(
    bucketId: Long?,
    scanner: PickerScanner,
    selected: SnapshotStateList<MediaItem>,
    onToggle: (MediaItem) -> Unit,
    onToggleGroup: (List<MediaItem>) -> Unit,
    running: Boolean
) {
    val state = remember(bucketId) { if (bucketId != null) scanner.stateOf(bucketId) else null }
    val results = state?.results

    if (results == null) return

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val sorted = results.sortedByDescending { it.sortTime }
        val groups = sorted.groupBy { dateKey(it.sortTime) }
        for ((dk, groupItems) in groups) {
            stickyHeader(key = "hdr_$dk") {
                val allSel = groupItems.all { g -> selected.any { it.id == g.id } }
                val someSel = groupItems.any { g -> selected.any { it.id == g.id } }
                DateHeader(
                    label = dateLabel(groupItems.first().sortTime),
                    checkState = when {
                        allSel -> ToggleableState.On
                        someSel -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onToggleAll = { onToggleGroup(groupItems) }
                )
            }
            items(groupItems, key = { it.key }) { item ->
                GridCell(
                    item = item,
                    selected = selected.any { it.id == item.id },
                    selectionIndex = selected.indexOfFirst { it.id == item.id },
                    onToggle = { onToggle(item) }
                )
            }
        }
        // 扫描中：网格底部加载圈
        if (running) {
            item(span = { GridItemSpan(3) }) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        }
    }
}

/** 相册 chip */
@Composable
private fun AlbumChip(
    album: AlbumInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val coverUri = android.net.Uri.parse(
                    android.provider.MediaStore.Images.Media.getContentUri(
                        android.provider.MediaStore.VOLUME_EXTERNAL).toString() + "/${album.coverId}"
                )
                MediaThumbnail(coverUri, resolver, Modifier.fillMaxSize())
            }
            Column {
                Text(
                    album.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${album.count} 张",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

/** 粘性日期标题：右端圆形三态复选框可全选/取消该日期下所有照片 */
@Composable
private fun DateHeader(label: String, checkState: ToggleableState, onToggleAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        CircleTriCheckbox(state = checkState, onClick = onToggleAll)
    }
}

/**
 * 网格单元：勾选徽章在右上角；选中以几何中心缩放 + 圆角增大过渡。
 */
@Composable
private fun GridCell(item: MediaItem, selected: Boolean, selectionIndex: Int, onToggle: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val transition = updateTransition(selected, label = "cell")

    // 选中时以几何中心为定点缩小
    val scale by transition.animateFloat(label = "scale") { if (it) 0.86f else 1f }
    // 圆角：未选 6dp、选中 16dp；以 0..1 分数动画映射 Dp
    val cornerFraction by transition.animateFloat(label = "cornerF") { if (it) 1f else 0f }
    val cornerRadius = lerp(6.dp, 16.dp, cornerFraction)

    // 徽章背景色：透明→primary（颜色平滑过渡）
    val badgeBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200), label = "badgeBg"
    )
    // 徽章以几何中心为基准的弹簧缩放
    val badgeScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "badgeScale"
    )

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
    ) {
        // 内层图片：中心缩放 + 圆角/边框动画
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            MediaThumbnail(item.uri, resolver, Modifier.fillMaxSize())
        }

        // 实况标记（左下角小圆点），提示这是动态照片
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(8.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
        )

        // 勾选徽章（右上角）：选中时 primary 圆底 + 白色序号，未选中空心圈
        val digits = if (selected) (selectionIndex + 1).toString().length else 1
        val badgeSize = when {
            digits >= 3 -> 28.dp
            digits == 2 -> 24.dp
            else -> 22.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .size(badgeSize)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .background(badgeBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = if (selected) selectionIndex else -1,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(tween(200))) togetherWith
                            (scaleOut(
                                targetScale = 0.5f,
                                animationSpec = tween(150)
                            ) + fadeOut(tween(150)))
                },
                label = "badge"
            ) { idx ->
                if (idx >= 0) {
                    Text(
                        text = "${idx + 1}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = 0.8f }
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}
