package com.vliveconvert.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.vliveconvert.app.core.PhotoTime
import com.vliveconvert.app.picker.AlbumInfo
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.picker.SingleLiveScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 修复文件时间界面（与双文件选择器同一套相册式交互）：
 * 扫描全部相册中的单文件实况（XMP 含 MotionPhoto/MicroVideo），多选后
 * 按文件名中的拍摄时间批量修正「修改时间」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixTimeScreen(
    albums: List<AlbumInfo>,
    scanner: SingleLiveScanner,
    scannerScope: CoroutineScope,
    isFixing: Boolean,
    progress: Float,
    statusText: String,
    selectionReset: Int,
    onBack: () -> Unit,
    onFix: (List<MediaItem>) -> Unit
) {
    var currentBucketId by remember { mutableStateOf(albums.firstOrNull()?.bucketId) }
    val bucketId = currentBucketId
    val selected = remember { mutableStateListOf<MediaItem>() }

    // 修复完成后清空选择
    LaunchedEffect(selectionReset) {
        if (selectionReset > 0) selected.clear()
    }

    DisposableEffect(bucketId) {
        if (bucketId != null) scanner.enter(bucketId, scannerScope)
        onDispose { if (bucketId != null) scanner.leave(bucketId) }
    }

    val state = remember(bucketId) { if (bucketId != null) scanner.stateOf(bucketId) else null }
    val results = state?.results
    val total = state?.total ?: 0
    val scanProgress by produceState(0 to false, state) {
        while (true) {
            value = (state?.doneCount?.get() ?: 0) to (state?.running ?: false)
            delay(200)
        }
    }
    val scanning = scanProgress.second

    val n = results?.size ?: 0
    val displayOrder = remember(results, n) {
        results.orEmpty().sortedByDescending { it.sortTime }
    }

    val toggleItem: (MediaItem) -> Unit = { item ->
        val idx = selected.indexOfFirst { it.id == item.id }
        if (idx >= 0) selected.removeAt(idx) else selected.add(item)
    }
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
                    Text("修复文件时间", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("按文件名中的拍摄时间修正「修改时间」",
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
            AnimatedVisibility(visible = scanning) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // ── 工具行 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (scanning) "扫描中 ${scanProgress.first}/$total" else "${results?.size ?: 0} 个单文件实况",
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

        // ── 状态条 ──
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }

        // ── 网格 ──
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when {
                results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可用相册", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                results.isEmpty() && !scanning -> Column(
                    Modifier.fillMaxSize().padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("没有待修复的单文件实况", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("仅显示修改时间与照片时间不一致的单文件实况；已一致的不会出现",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    dateLabel(groupItems.first().sortTime),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                CircleTriCheckbox(
                                    state = when {
                                        allSel -> ToggleableState.On
                                        someSel -> ToggleableState.Indeterminate
                                        else -> ToggleableState.Off
                                    },
                                    onClick = { toggleGroup(groupItems) }
                                )
                            }
                        }
                        items(groupItems, key = { it.key }) { item ->
                            FixCell(
                                item = item,
                                selected = selected.any { it.id == item.id },
                                onToggle = { toggleItem(item) }
                            )
                        }
                    }
                    if (scanning) {
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
        }

        // ── 底栏 ──
        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isFixing) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selected.isEmpty()) "" else "已选 ${selected.size} 项",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onFix(selected.toList()) },
                        enabled = selected.isNotEmpty() && !isFixing
                    ) { Text(if (isFixing) "修复中…" else "修复所选时间") }
                }
            }
        }
    }
}

@Composable
private fun AlbumChip(album: AlbumInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            album.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** 底部显示目标时间：文件名时间 → 拍摄时间 → 无 */
private fun targetTimeText(item: MediaItem): String {
    val t = PhotoTime.parseFromName(item.name)
        ?: (if (item.dateTaken > 0) item.dateTaken else 0L)
    return if (t > 0) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t)) else "无时间"
}

@Composable
private fun FixCell(item: MediaItem, selected: Boolean, onToggle: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val transition = updateTransition(selected, label = "fixCell")
    val scale by transition.animateFloat(label = "scale") { if (it) 0.88f else 1f }
    val cornerFraction by transition.animateFloat(label = "cornerF") { if (it) 1f else 0f }
    val cornerRadius = lerp(6.dp, 16.dp, cornerFraction)
    val badgeBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
        animationSpec = tween(200), label = "badgeBg"
    )
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
        // 底部：文件名 + 目标时间
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                item.name,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "→ ${targetTimeText(item)}",
                color = if (targetTimeText(item) == "无时间") Color(0xFFFFCDD2) else Color.White,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        // 右上角勾选徽章
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .background(badgeBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
