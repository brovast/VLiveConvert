package com.vliveconvert.app.convert

import com.vliveconvert.app.core.VivoDual
import com.vliveconvert.app.core.VivoSingle
import java.io.File

/**
 * 转换管线：vivo 双文件实况（IMG_xxx.jpg + IMG_xxx.mp4）→ vivo 单文件实况（单个 .jpg）。
 * 全程字节级无损透传：JPEG 原样拆分重组、MP4 仅剥离尾部 uuid box 并插入 lpex，不重编码。
 */
internal class ConvertException(message: String) : Exception(message)

internal object Converter {

    /**
     * 把一个 vivo 双文件实况转换为单文件实况，返回输出文件路径。
     *
     * @param path 源 JPG 路径（同目录须存在同名 .mp4 伴生视频）
     * @param outDir 输出目录
     * @param log 日志回调 (level, message, tag)
     */
    fun convertToVivoSingle(
        path: String, outDir: String,
        log: (String, String, String) -> Unit
    ): String {
        val src = File(path)
        if (!src.exists()) throw ConvertException("源文件不存在：$path")

        if (!VivoDual.isVivoDualFile(path)) {
            throw ConvertException(
                "不是 vivo 双文件实况照片（缺少伴生 .mp4 视频或实况标记）")
        }
        log("info", "识别为 vivo 双文件实况照片", "转换")

        val asset = VivoDual.read(path, log)

        val stem = src.nameWithoutExtension
        val outPath = VivoSingle.write(asset, outDir, stem, log)

        // 保留源文件的修改时间（原文件名中的时间信息不丢失）
        try {
            File(outPath).setLastModified(src.lastModified())
        } catch (e: Exception) {
            /* 时间戳复制失败不阻塞转换 */
        }
        return outPath
    }
}
