package com.vliveconvert.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * 窗口内玻璃底部面板（非独立 Dialog 窗口，因此可以采样主界面的 backdrop）。
 *
 * 面板内部再导出 [Backdrop]，供 [GlassSlider]/[GlassToggle] 实现"玻璃叠玻璃"。
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    maxHeightFraction: Float = 0.88f,
    content: @Composable ColumnScope.(sheetBackdrop: Backdrop) -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(280))
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩：点击关闭
            Box(
                Modifier
                    .fillMaxSize()
                    .background(GlassDefaults.scrimColor())
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    }
            )

            BoxWithConstraints(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxSheetHeight: Dp = maxHeight * maxHeightFraction
                val sheetBackdrop = rememberLayerBackdrop()
                var dragOffset by remember { mutableFloatStateOf(0f) }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) { it } + fadeIn(tween(220)),
                    exit = slideOutVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) { it } + fadeOut(tween(180))
                ) {
                    GlassSurface(
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                            // 吞掉面板空白处的点击，避免穿透到后面的遮罩导致误关闭
                            .pointerInput(Unit) { detectTapGestures { } },
                        shape = RoundedRectangle(36.dp),
                        quality = quality,
                        enabled = true,
                        interactive = false,
                        surfaceColor = GlassDefaults.strongSurfaceColor(),
                        highlight = Highlight.Plain,
                        shadow = Shadow(
                            radius = 28.dp,
                            color = Color.Black.copy(alpha = 0.24f)
                        ),
                        exportedBackdrop = sheetBackdrop
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 拖拽把手
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, amount ->
                                                change.consume()
                                                dragOffset = (dragOffset + amount)
                                                    .coerceAtLeast(0f)
                                            },
                                            onDragEnd = {
                                                if (dragOffset > 72.dp.toPx()) {
                                                    // 保留下拉位移，让退场动画从当前位置继续下滑
                                                    onDismiss()
                                                } else {
                                                    dragOffset = 0f
                                                }
                                            },
                                            onDragCancel = { dragOffset = 0f }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp, 4.dp)
                                        .clip(Capsule())
                                        .background(
                                            GlassDefaults.contentColor().copy(alpha = 0.28f)
                                        )
                                )
                            }

                            content(sheetBackdrop)
                        }
                    }
                }
            }
        }
    }
}
