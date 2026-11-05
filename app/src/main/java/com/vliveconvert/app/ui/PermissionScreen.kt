package com.vliveconvert.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.vliveconvert.glass.GlassBackground
import com.vliveconvert.glass.GlassDefaults
import com.vliveconvert.glass.GlassPillButton
import com.vliveconvert.glass.GlassQuality
import com.vliveconvert.glass.GlassScaffold
import com.vliveconvert.glass.GlassSurface

/**
 * 权限引导界面（未授予媒体权限时显示）：渐变背景 + 玻璃卡片。
 */
@Composable
fun PermissionScreen(
    onRequest: () -> Unit,
    statusText: String,
    quality: GlassQuality
) {
    val glassActive = quality != GlassQuality.Off

    GlassScaffold(
        glassEnabled = glassActive,
        background = { GlassBackground(Modifier.fillMaxSize()) },
        content = {},
        overlays = { backdrop ->
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedRectangle(32.dp),
                    quality = quality,
                    enabled = glassActive,
                    interactive = false,
                    surfaceColor = GlassDefaults.strongSurfaceColor(),
                    highlight = Highlight.Plain,
                    shadow = Shadow(radius = 26.dp, color = Color.Black.copy(alpha = 0.16f))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 26.dp)
                    ) {
                        Text(
                            "VLiveConvert",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "vivo 双文件实况 → 单文件实况",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            "本应用需要读取您设备上的照片和视频：\n\n" +
                                "• 照片权限：扫描设备相册，识别其中的双文件实况照片\n" +
                                "• 视频权限：查找双文件实况附带的伴生视频\n" +
                                "• 位置权限：保留照片中的 GPS 位置元数据\n\n" +
                                "转换全程字节级无损，不重编码，不会上传任何数据。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(26.dp))
                        GlassPillButton(
                            backdrop = backdrop,
                            onClick = onRequest,
                            modifier = Modifier.fillMaxWidth(),
                            quality = quality,
                            enabled = true,
                            interactive = true,
                            tint = Color(0xFF415FFF),
                            surfaceColor = Color.White.copy(alpha = 0.10f)
                        ) {
                            Text(
                                "授予权限",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        if (statusText.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}
