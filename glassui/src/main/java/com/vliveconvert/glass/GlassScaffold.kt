package com.vliveconvert.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 玻璃页面骨架：
 *
 * - [background] 与 [content] 都进入 backdrop 采样层（列表滚动时玻璃会折射/模糊它们）
 * - [overlays] 绘制玻璃元素，必须位于采样层之外，避免自己采样自己
 *
 * [glassEnabled] 为 false 时移除 `layerBackdrop`，滚动期间不再每帧录制整层内容。
 */
@Composable
fun GlassScaffold(
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
    overlays: @Composable BoxScope.(Backdrop) -> Unit
) {
    Box(modifier.fillMaxSize()) {
        val backdrop = rememberLayerBackdrop()

        Box(
            Modifier
                .fillMaxSize()
                .then(if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            background()
            content()
        }

        overlays(backdrop)
    }
}
