package com.vliveconvert.app.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.state.ToggleableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 缩略图内存 LRU 缓存（按需加载）：
 * 每次仅组合屏幕可见区域附近的网格项，滚动回来命中缓存直接显示。
 */
val thumbCache = LruCache<String, Bitmap>(96)

// 缩略图加载有界并发：限制同时进行的 ContentResolver.loadThumbnail 数量，
// 避免快速滚动/千张列表时并发 I/O 过多拖垮主线程与磁盘
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
val thumbDispatcher = Dispatchers.IO.limitedParallelism(4)

/**
 * 系统缩略图（loadThumbnail，走系统缓存不落盘）：
 * - 按需加载：仅组合屏幕可见区域附近的项；命中 LRU 缓存直接显示
 * - 加载动画：占位呼吸脉冲，加载完成淡入+缩放进入
 */
@Composable
fun MediaThumbnail(uri: Uri, resolver: ContentResolver, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, uri) {
        val key = uri.toString()
        // 命中缓存（滚动回来）：直接显示，不重复加载
        thumbCache.get(key)?.let { value = it; return@produceState }
        value = withContext(thumbDispatcher) {
            val b = try {
                resolver.loadThumbnail(uri, Size(160, 160), null)
            } catch (_: Exception) {
                null
            }
            if (b != null) thumbCache.put(key, b)
            b
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = bitmap != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
            exit = fadeOut(tween(150))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // 加载占位：呼吸脉冲
        if (bitmap == null) {
            val pulse by rememberInfiniteTransition(label = "thumbPulse").animateFloat(
                initialValue = 0.45f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
                label = "pulseAlpha"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
            )
        }
    }
}

/**
 * 圆形三态复选框：
 * - Off：外圈描边
 * - Indeterminate：primary 横线
 * - On：primary 填充 + 白色对勾
 */
@Composable
fun CircleTriCheckbox(
    state: ToggleableState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val borderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick?.invoke() }
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp)) {
            val s = size.minDimension
            val stroke = 2.dp.toPx()
            when (state) {
                ToggleableState.On -> {
                    drawCircle(fillColor, radius = s / 2 - stroke / 2 - 1.dp.toPx())
                    drawLine(
                        color = checkColor,
                        start = Offset(s * 0.30f, s * 0.53f),
                        end = Offset(s * 0.45f, s * 0.68f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = checkColor,
                        start = Offset(s * 0.45f, s * 0.68f),
                        end = Offset(s * 0.72f, s * 0.34f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                ToggleableState.Indeterminate -> {
                    drawLine(
                        color = fillColor,
                        start = Offset(s * 0.28f, s / 2),
                        end = Offset(s * 0.72f, s / 2),
                        strokeWidth = stroke * 1.2f,
                        cap = StrokeCap.Round
                    )
                }
                ToggleableState.Off -> {}
            }
        }
    }
}

/** 日期 key（年-月-日） */
fun dateKey(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
}

/** 日期标签：今天 / 昨天 / x月x日（今年） / yyyy年x月x日（往年） */
fun dateLabel(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(cal, now) -> "今天"
        sameDay(cal, yesterday) -> "昨天"
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        else ->
            "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    }
}
