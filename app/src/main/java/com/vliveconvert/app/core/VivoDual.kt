package com.vliveconvert.app.core

import java.io.File
import java.io.FileInputStream
import kotlin.math.round

/**
 * vivo 双文件实况照片（IMG_xxx.jpg + IMG_xxx.mp4）的识别与解析。
 * 逻辑取自 ZLivePhoto 项目的 VivoPlugin（detect/read），经真机验证。
 *
 * 双文件 JPG 尾部布局：[JPEG 主体（Primary + GainMap）]["vivo" 前缀][footer JSON][cameralbum! footer]，
 * footer 内含 livephoto ID（28 字符）与 imageTime（封面帧序号）；
 * 伴生 MP4 尾部：[vivoMediaEStream uuid][vivoMediaExtInfo uuid（内嵌源 footer 包装）]。
 */
internal object VivoDual {

    /** 同目录同名 .mp4 伴生视频路径；不存在返回 null。 */
    fun siblingMp4(path: String): String? {
        val file = File(path)
        val parent = file.parentFile
        val mp4 = if (parent != null) File(parent, "${file.nameWithoutExtension}.mp4").path
                  else "${file.nameWithoutExtension}.mp4"
        return if (File(mp4).exists()) mp4 else null
    }

    /**
     * 是否为 vivo 双文件实况照片：
     * 1) JPG 扩展名；2) 存在同目录同名 .mp4；
     * 3) XMP 无内嵌动态照片标记（排除 Google/OPPO/小米等内嵌单文件）；
     * 4) JPG 尾部能解析出含 livephoto ID 的 cameralbum! footer。
     */
    fun isVivoDualFile(path: String): Boolean {
        if (!path.endsWith(".jpg", ignoreCase = true) &&
            !path.endsWith(".jpeg", ignoreCase = true)
        ) return false
        if (siblingMp4(path) == null) return false
        if (XmpTemplate.parseMotionXmp(XmpTemplate.sniffXmp(path)).isMotion) return false

        return try {
            val f = File(path)
            val size = f.length()
            FileInputStream(path).use { fs ->
                fs.skip(maxOf(0L, size - 8192))
                val tail = ByteArray(minOf(8192L, size).toInt())
                var read = 0
                while (read < tail.size) {
                    val n = fs.read(tail, read, tail.size - read)
                    if (n < 0) break
                    read += n
                }
                val footer = FooterUtil.parseFooter(tail.copyOfRange(0, read))
                footer?.livephotoId != null
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析为 LivePhotoAsset（字节级无损：JPEG 原样拆分，MP4 仅剥离尾部 uuid box）。
     */
    fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按 vivo 双文件实况照片解析", "vivo")
        val data = File(path).readBytes()
        val footer = FooterUtil.parseFooter(data)
            ?: throw VivoDualException("JPG 尾部未找到 vivo livephoto 标记")

        val liveId = footer.livephotoId!!
        val mp4Path = siblingMp4(path)
            ?: throw VivoDualException("缺少伴生视频文件：${File(path).nameWithoutExtension}.mp4")

        // JPG 主体（去除 footer）→ 拆 Primary / GainMap / streamdata 附加块
        val body = data.copyOfRange(0, footer.footerStart)
        val (jpegs, consumed) = JpegUtil.splitJpegs(body)
        if (jpegs.isEmpty()) throw VivoDualException("JPG 主体解析失败")
        val primary = jpegs[0]
        val gainmap = if (jpegs.size > 1) jpegs[1] else null
        // JPEG 之后的附加数据块（"streamdata" 魔数开头的 vivo 私有流）：
        // 普通实况约 114B（DEGS 流信息）；人像实况约 4MB（IAC 深度/虚化数据，
        // 丢失会导致相册不再显示人像徽标、无法后编辑光圈/虚化）。必须原样透传。
        val streamData = if (consumed < body.size) body.copyOfRange(consumed, body.size) else ByteArray(0)

        // MP4：剥离末尾 vivoMediaExtInfo uuid box（内嵌源 footer 包装）
        val mp4Raw = File(mp4Path).readBytes()
        val mp4Footer = FooterUtil.parseFooter(mp4Raw)
        var imageTime: Long? = footer.imageTime
        if (mp4Footer != null) {
            if (imageTime == null) imageTime = mp4Footer.imageTime
            val mp4Id = mp4Footer.livephotoId
            if (mp4Id != null && mp4Id != liveId) {
                log("warning", "JPG 与 MP4 的 livephoto ID 不一致：$liveId / $mp4Id", "vivo")
            }
        }

        val video = Mp4Util.stripVivoUuid(mp4Raw)
        if (!Mp4Util.hasFtyp(video))
            throw VivoDualException("伴生 MP4 无效（缺少 ftyp box）")

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = gainmap,
            videoMp4 = video,
            sourceFormat = "vivo_dual",
        )
        asset.livephotoId = liveId
        asset.imageTime = imageTime
        asset.videoInfo = Mp4Util.getTrackInfo(video) ?: mutableMapOf()
        // 透传数据：streamdata 附加块 + 源 JPG/MP4 footer 的完整字段
        // （供单文件写出时合并人像标记 moduleid/joint.refocus 等）
        asset.extras["vivo_streamdata"] = streamData
        footer.json?.let { asset.extras["vivo_jpg_footer"] = it }
        mp4Footer?.json?.let { asset.extras["vivo_mp4_footer"] = it }

        // 由 imageTime（帧序号）反推封面时间戳
        val imageTimeVal = asset.imageTime
        val fps = asset.videoInfo["fps"] as? Double
        if (imageTimeVal != null && fps != null && fps > 0.0) {
            asset.presentationTsUs = round(imageTimeVal.toDouble() / fps * 1_000_000.0).toLong()
        }
        return asset
    }
}

internal class VivoDualException(message: String) : Exception(message)
