package com.vliveconvert.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 玻璃特效画质档位。
 *
 * - [Off]：完全关闭玻璃管线，使用半透明纯色面（最省电）
 * - [Low]：仅模糊 + 提亮，不做折射
 * - [Medium]：模糊 + 折射 + 高光
 * - [High]：模糊 + 折射 + 深度 + 按压色散 + 渐进模糊边缘
 */
enum class GlassQuality { Off, Low, Medium, High }

/** 当前画质档位；由设置面板写入。 */
val LocalGlassQuality = compositionLocalOf { GlassQuality.Medium }

object GlassDefaults {

    @Composable
    fun isDark(): Boolean = isSystemInDarkTheme()

    /** 普通玻璃面：浅色偏白、深色偏黑，均保持较高透明度。 */
    @Composable
    fun surfaceColor(): Color =
        if (isDark()) Color(0xFF121318).copy(alpha = 0.44f)
        else Color.White.copy(alpha = 0.50f)

    /** 大面板（设置页、弹窗）需要更高的可读性。 */
    @Composable
    fun strongSurfaceColor(): Color =
        if (isDark()) Color(0xFF121318).copy(alpha = 0.72f)
        else Color.White.copy(alpha = 0.74f)

    /** 设置页/弹窗后面的遮罩。 */
    @Composable
    fun scrimColor(): Color =
        if (isDark()) Color.Black.copy(alpha = 0.52f)
        else Color(0xFF29293A).copy(alpha = 0.28f)

    /** 玻璃上的文字颜色。 */
    @Composable
    fun contentColor(): Color = MaterialTheme.colorScheme.onSurface

    /** 强调色（开始转换、滑块、选中态）。 */
    @Composable
    fun accentColor(): Color = MaterialTheme.colorScheme.primary

    /** 未选中的轨道/底色。 */
    @Composable
    fun trackColor(): Color =
        if (isDark()) Color(0xFF787880).copy(alpha = 0.36f)
        else Color(0xFF787878).copy(alpha = 0.20f)

    /** 玻璃按钮的按压态缩放幅度。 */
    const val PRESS_SCALE = 0.04f
}
