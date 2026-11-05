package com.vliveconvert.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 统一的玻璃面板。
 *
 * [enabled] 为 false 时（关闭特效 / 滚动中）自动退化为半透明纯色面，
 * 不再记录 backdrop 图层，也不产生 RenderEffect 开销。
 *
 * 注意：玻璃元素必须是被 [com.kyant.backdrop.backdrops.layerBackdrop] 采样内容的
 * 兄弟节点，不能放进采样子树，否则会自己采样自己。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true,
    interactive: Boolean = false,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = GlassDefaults.surfaceColor(),
    highlight: Highlight? = Highlight.Default,
    shadow: Shadow? = Shadow.Default,
    innerShadow: InnerShadow? = null,
    blurRadius: androidx.compose.ui.unit.Dp = 8.dp,
    lensHeight: androidx.compose.ui.unit.Dp = 14.dp,
    lensAmount: androidx.compose.ui.unit.Dp = 24.dp,
    exportedBackdrop: LayerBackdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isGlass = enabled && quality != GlassQuality.Off
    val scope = rememberCoroutineScope()
    // 只有可交互表面才创建 RuntimeShader / 手势，避免静态面板产生无谓的 GPU 初始化
    val interaction = remember(scope, interactive) {
        if (interactive) InteractiveHighlight(scope) else null
    }
    val interactiveHighlight = interaction

    val highlightProvider = remember(highlight) { highlight?.let { value -> { value } } }
    val shadowProvider = remember(shadow) { shadow?.let { value -> { value } } }
    val innerShadowProvider = remember(innerShadow) { innerShadow?.let { value -> { value } } }

    val effects: BackdropEffectScope.() -> Unit =
        remember(quality, interactiveHighlight, blurRadius, lensHeight, lensAmount) {
            {
                when (quality) {
                    GlassQuality.Low -> {
                        vibrancy()
                        blur((blurRadius * 0.6f).toPx())
                    }

                    GlassQuality.Medium -> {
                        vibrancy()
                        blur((blurRadius * 0.8f).toPx())
                        lens(
                            (lensHeight * 0.78f).toPx(),
                            (lensAmount * 0.75f).toPx()
                        )
                    }

                    GlassQuality.High -> {
                        vibrancy()
                        blur(blurRadius.toPx())
                        lens(
                            refractionHeight = lensHeight.toPx(),
                            refractionAmount = lensAmount.toPx(),
                            depthEffect = true,
                            chromaticAberration =
                                (interactiveHighlight?.pressProgress ?: 0f) > 0.01f
                        )
                    }

                    GlassQuality.Off -> Unit
                }
            }
        }

    val drawSurface: DrawScope.() -> Unit = remember(tint, surfaceColor) {
        {
            if (tint.isSpecified) {
                drawRect(tint, blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = 0.72f))
            }
            drawRect(surfaceColor)
        }
    }

    val glassLayerBlock: (GraphicsLayerScope.() -> Unit)? =
        if (interactiveHighlight != null && isGlass) {
            {
                val scale = 1f + GlassDefaults.PRESS_SCALE * interactiveHighlight.pressProgress
                scaleX = scale
                scaleY = scale
            }
        } else {
            null
        }

    // 非玻璃路径也要有按压缩放反馈
    val fallbackScale =
        if (interactiveHighlight != null && !isGlass) {
            Modifier.graphicsLayer {
                val scale = 1f + GlassDefaults.PRESS_SCALE * interactiveHighlight.pressProgress
                scaleX = scale
                scaleY = scale
            }
        } else {
            Modifier
        }

    val fallbackSurface =
        if (tint.isSpecified) tint.copy(alpha = 0.92f) else surfaceColor

    val surfaceModifier =
        if (isGlass) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = effects,
                highlight = highlightProvider,
                shadow = shadowProvider,
                innerShadow = innerShadowProvider,
                layerBlock = glassLayerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = drawSurface
            )
        } else {
            Modifier.clip(shape).background(fallbackSurface)
        }

    val contentClip = if (isGlass) Modifier else Modifier.clip(shape)

    val interactionModifiers =
        if (interactiveHighlight != null) {
            Modifier
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
        } else {
            Modifier
        }

    Box(
        modifier
            .then(fallbackScale)
            .then(surfaceModifier)
            .then(contentClip)
            .then(interactionModifiers)
    ) {
        content()
    }
}
