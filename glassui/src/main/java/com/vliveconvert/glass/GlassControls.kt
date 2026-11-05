package com.vliveconvert.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 玻璃滑块：轨道为半透明胶囊，滑块为独立玻璃胶囊。 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    val trackColor = GlassDefaults.trackColor()
    val accent = GlassDefaults.accentColor()
    val knobSurface =
        if (GlassDefaults.isDark()) Color(0xFFE6E8F0).copy(alpha = 0.92f)
        else Color.White.copy(alpha = 0.94f)

    var dragging by remember { mutableStateOf(false) }
    val knobScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "sliderKnobScale"
    )

    fun snap(raw: Float): Float {
        val coerced = raw.coerceIn(valueRange)
        if (steps <= 0) return coerced
        val stepSize = range / steps
        return (valueRange.start + ((coerced - valueRange.start) / stepSize).roundToInt() * stepSize)
            .coerceIn(valueRange)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(38.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val knobWidthPx = with(density) { 42.dp.toPx() }
        val maxOffset = (trackWidthPx - knobWidthPx).coerceAtLeast(0f)

        fun valueAt(x: Float): Float {
            val f = (x / trackWidthPx).coerceIn(0f, 1f)
            return snap(valueRange.start + range * f)
        }

        // 轨道
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(Capsule())
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(Capsule())
                    .background(accent)
            )
        }

        // 触摸层：点击定位 + 横向拖动
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(valueRange, steps) {
                            detectTapGestures { position ->
                                dragging = false
                                currentOnValueChange(valueAt(position.x))
                                currentOnFinished?.invoke()
                            }
                        }
                    } else Modifier
                )
                .then(
                    if (enabled) {
                        Modifier.pointerInput(valueRange, steps) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    currentOnFinished?.invoke()
                                },
                                onDragCancel = { dragging = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val next = snap(
                                    currentValue + range * (dragAmount / trackWidthPx)
                                )
                                currentOnValueChange(next)
                            }
                        }
                    } else Modifier
                )
        )

        // 玻璃滑块
        Box(
            Modifier
                .offset { IntOffset((fraction * maxOffset).roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = knobScale
                    scaleY = knobScale
                }
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.size(42.dp, 26.dp),
                shape = Capsule(),
                quality = quality,
                enabled = enabled,
                interactive = false,
                surfaceColor = knobSurface,
                highlight = Highlight.Ambient,
                shadow = Shadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.16f)),
                innerShadow = InnerShadow(radius = 3.dp, alpha = 0.10f),
                blurRadius = 6.dp,
                lensHeight = 8.dp,
                lensAmount = 12.dp
            ) {}
        }
    }
}

/** 玻璃开关：轨道颜色随状态渐变，滑块为独立玻璃圆。 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val fraction = remember { Animatable(if (checked) 1f else 0f) }
    val targetFraction by rememberUpdatedState(if (checked) 1f else 0f)
    val onChecked by rememberUpdatedState(onCheckedChange)
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        fraction.animateTo(
            targetValue = targetFraction,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    val trackWidth = 58.dp
    val knobSize = 26.dp
    val padding = 3.dp
    val trackColor = GlassDefaults.trackColor()
    val accent = GlassDefaults.accentColor()
    val knobSurface =
        if (GlassDefaults.isDark()) Color(0xFFE6E8F0).copy(alpha = 0.94f)
        else Color.White.copy(alpha = 0.96f)
    val knobScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "toggleKnobScale"
    )

    BoxWithConstraints(
        modifier
            .size(trackWidth, 32.dp)
            .clip(Capsule())
            .drawBehind {
                drawRect(lerp(trackColor, accent, fraction.value))
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onChecked(!checked) }
            }
            .pointerInput(enabled, checked) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val next = fraction.value >= 0.5f
                        if (next != checked) onChecked(next)
                        scope.launch {
                            fraction.animateTo(
                                if (next) 1f else 0f,
                                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch {
                            fraction.animateTo(
                                targetFraction,
                                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val trackPx = size.width.toFloat()
                    val delta = dragAmount / trackPx
                    scope.launch {
                        fraction.snapTo((fraction.value + delta).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val travel = with(density) { (trackWidth - knobSize - padding * 2).toPx() }
        val start = with(density) { padding.toPx() }
        Box(
            Modifier
                .offset { IntOffset((start + travel * fraction.value).roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = knobScale
                    scaleY = knobScale
                }
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.size(knobSize),
                shape = CircleShape,
                quality = quality,
                enabled = enabled,
                interactive = false,
                surfaceColor = knobSurface,
                highlight = Highlight.Ambient,
                shadow = Shadow(radius = 5.dp, color = Color.Black.copy(alpha = 0.18f)),
                innerShadow = InnerShadow(radius = 2.dp, alpha = 0.10f),
                blurRadius = 5.dp,
                lensHeight = 7.dp,
                lensAmount = 10.dp
            ) {}
        }
    }
}

/** 玻璃分段选择器（设置面板的性能档位/预设）。 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = Capsule(),
        quality = quality,
        enabled = enabled,
        interactive = false,
        surfaceColor = GlassDefaults.surfaceColor(),
        shadow = Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.08f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val background by animateColorAsState(
                    targetValue =
                        if (!enabled) {
                            GlassDefaults.trackColor().copy(alpha = 0.3f)
                        } else if (isSelected) {
                            GlassDefaults.accentColor()
                        } else {
                            Color.Transparent
                        },
                    label = "segmentBg$index"
                )
                val contentColor =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(Capsule())
                        .background(background)
                        .glassClickable(enabled) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
