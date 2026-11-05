package com.vliveconvert.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule

/** 无涟漪点击（玻璃按钮的反馈由 InteractiveHighlight 负责）。 */
@Composable
fun Modifier.glassClickable(
    enabled: Boolean,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick
    )
}

/**
 * 圆形玻璃图标按钮（添加照片、顶栏设置等）。
 * 尺寸由调用方通过 [modifier] 指定。
 */
@Composable
fun GlassIconButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true,
    interactive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = GlassDefaults.surfaceColor(),
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.36f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassIconAlpha"
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.glassClickable(enabled, onClick),
        shape = shape,
        quality = quality,
        enabled = enabled,
        interactive = interactive && enabled,
        tint = tint,
        surfaceColor = surfaceColor
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/** 胶囊玻璃按钮（开始转换、添加确认等）。 */
@Composable
fun GlassPillButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true,
    interactive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = GlassDefaults.surfaceColor(),
    height: Int = 56,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable RowScope.() -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassPillAlpha"
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.glassClickable(enabled, onClick),
        shape = Capsule(),
        quality = quality,
        enabled = enabled,
        interactive = interactive && enabled,
        tint = tint,
        surfaceColor = surfaceColor
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .graphicsLayer { this.alpha = alpha }
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** 一整块玻璃胶囊，内部横向排列图标按钮（顶栏下的三按钮工具栏）。 */
@Composable
fun GlassCapsuleToolbar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    quality: GlassQuality = LocalGlassQuality.current,
    enabled: Boolean = true,
    surfaceColor: Color = GlassDefaults.surfaceColor(),
    content: @Composable RowScope.() -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = Capsule(),
        quality = quality,
        enabled = enabled,
        interactive = false,
        surfaceColor = surfaceColor
    ) {
        Row(
            Modifier
                .height(52.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** 胶囊工具栏内的单个图标按钮（无独立玻璃面）。 */
@Composable
fun GlassToolbarIcon(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "toolbarIconScale"
    )
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
