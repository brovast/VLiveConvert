package com.vliveconvert.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 应用内补充图标（Material Icons 路径，Apache-2.0）。
 *
 * 只补 material-icons-core 里没有的几个，避免引入 35MB 的 extended 依赖。
 */
object AppIcons {

    val Schedule: ImageVector by lazy {
        icon(
            name = "Schedule",
            path = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 " +
                "22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 " +
                "8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"
        )
    }

    val PhotoCamera: ImageVector by lazy {
        icon(
            name = "PhotoCamera",
            path = "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 " +
                "2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 " +
                "5-5 5 2.24 5 5-2.24 5-5 5z"
        )
    }

    val DeleteSweep: ImageVector by lazy {
        icon(
            name = "DeleteSweep",
            path = "M15 16h4v2h-4zm0-8h7v2h-7zm0 4h6v2h-6zM3 18c0 1.1.9 2 2 2h6c1.1 " +
                "0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6L5 5H2v2h12z"
        )
    }

    val PhotoLibrary: ImageVector by lazy {
        icon(
            name = "PhotoLibrary",
            path = "M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 " +
                "0 2-.9 2-2zm-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 " +
                "2h14v-2H4V6H2z"
        )
    }

    val Restore: ImageVector by lazy {
        icon(
            name = "Restore",
            path = "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 " +
                "3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 " +
                "1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 " +
                "5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
        )
    }

    val ViewList: ImageVector by lazy {
        icon(
            name = "ViewList",
            path = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 " +
                "4h14v-2H7v2zM7 7v2h14V7H7z"
        )
    }

    val GridView: ImageVector by lazy {
        icon(
            name = "GridView",
            path = "M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm10 0h8v8h-8v-8z"
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black)
        ).build()
}
