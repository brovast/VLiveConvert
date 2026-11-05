package com.vliveconvert.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 应用背景：品牌蓝/紫渐变 + 三团柔光。
 *
 * 玻璃效果需要背景有一定色彩层次，否则折射与模糊不可见。
 * [animated] 默认关闭：动画会让 backdrop 每一帧重新录制，持续消耗 GPU。
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    val dark = GlassDefaults.isDark()

    val phase: Float =
        if (animated) {
            val transition = rememberInfiniteTransition(label = "glassBackground")
            val value by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(28_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glassBackgroundPhase"
            )
            value
        } else {
            remember { mutableFloatStateOf(0f) }.floatValue
        }

    val baseColors =
        if (dark) {
            listOf(Color(0xFF07080C), Color(0xFF0D1220), Color(0xFF0A0B10))
        } else {
            listOf(Color(0xFFF3F6FF), Color(0xFFFDF9FF), Color(0xFFEFF7FF))
        }

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            Brush.linearGradient(
                colors = baseColors,
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        fun glow(color: Color, centerX: Float, centerY: Float, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )
        }

        val shift = phase * 0.06f
        val blue = if (dark) Color(0xFF415FFF) else Color(0xFF415FFF)
        val violet = if (dark) Color(0xFF8B5CF6) else Color(0xFF9B7BFF)
        val cyan = if (dark) Color(0xFF22D3EE) else Color(0xFF67D9F0)

        glow(blue, w * (0.05f + shift), h * 0.08f, w * 1.05f, if (dark) 0.34f else 0.22f)
        glow(violet, w * (0.98f - shift), h * 0.30f, w * 0.95f, if (dark) 0.26f else 0.16f)
        glow(cyan, w * (0.60f + shift), h * 0.92f, w * 0.90f, if (dark) 0.18f else 0.13f)
    }
}
