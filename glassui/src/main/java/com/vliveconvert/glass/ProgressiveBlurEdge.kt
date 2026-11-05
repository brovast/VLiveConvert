package com.vliveconvert.glass

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect

private const val ProgressiveBlurMaskShader = """
uniform shader content;
uniform float2 size;
uniform float fromTop;

half4 main(float2 coord) {
    float safeHeight = max(size.y, 1.0);
    float t = mix(coord.y / safeHeight, 1.0 - coord.y / safeHeight, fromTop);
    float a = smoothstep(0.0, 0.72, t);
    return content.eval(coord) * a;
}
"""

/**
 * 列表上下边缘的渐进模糊（Progressive blur）。
 *
 * 只在 [GlassQuality.High] 且玻璃启用时绘制；否则直接不占位。
 */
@Composable
fun ProgressiveBlurEdge(
    backdrop: Backdrop,
    fromTop: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true
) {
    if (!enabled || quality != GlassQuality.High) return

    val shader = remember { ProgressiveBlurMaskShader }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(6.dp.toPx())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        effect(
                            RenderEffect.createRuntimeShaderEffect(
                                obtainRuntimeShader(
                                    if (fromTop) "ProgressiveBlurMaskTop"
                                    else "ProgressiveBlurMaskBottom",
                                    shader
                                ).apply {
                                    setFloatUniform("size", size.width, size.height)
                                    setFloatUniform("fromTop", if (fromTop) 1f else 0f)
                                },
                                "content"
                            )
                        )
                    }
                }
            )
    )
}
