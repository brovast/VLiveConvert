package com.vliveconvert.app.core

import kotlin.math.round

/**
 * 一张动态照片的规范化表示（所有字节均为无损透传）。
 */
internal class LivePhotoAsset(
    /** 主 JPEG（SOI 到 EOI，含全部 APPn 段） */
    val primaryJpeg: ByteArray,
    /** GainMap JPEG（Ultra HDR，无则 null） */
    val gainmapJpeg: ByteArray?,
    /** 纯 MP4 流（不含厂商附加数据/footer） */
    val videoMp4: ByteArray,
    /** 来源格式标识 */
    var sourceFormat: String = "",
) {
    /** 封面帧视频内时间戳（微秒），-1 未知 */
    var presentationTsUs: Long = -1L

    /** vivo 关联 ID（28 字符） */
    var livephotoId: String? = null

    /** footer JSON 的 imageTime（封面帧序号） */
    var imageTime: Long? = null

    /** 视频轨信息：codec/width/height/fps/duration_us/rotation */
    var videoInfo: MutableMap<String, Any?> = mutableMapOf()

    /** 厂商私有附加（仅供同格式参考） */
    val extras: MutableMap<String, Any?> = mutableMapOf()

    val gainmapLength: Int?
        get() = gainmapJpeg?.size

    /** 封面帧时间戳：未知时按 Google 规范取视频中点。 */
    fun effectivePtsUs(): Long {
        if (presentationTsUs >= 0) return presentationTsUs
        val dur = videoInfo["duration_us"] as? Long
        if (dur != null && dur > 0L) return dur / 2
        return -1L
    }

    /** footer JSON 的 imageTime（封面帧序号）= round(pts * fps)。 */
    fun effectiveImageTime(): Long {
        imageTime?.let { return it }
        val pts = effectivePtsUs()
        if (pts >= 0) {
            val fps = videoInfo["fps"] as? Double
            if (fps != null && fps > 0.0) {
                return round(pts / 1_000_000.0 * fps).toLong()
            }
        }
        val fc = videoInfo["frame_count"] as? Long
        if (fc != null) return fc / 2
        return 0L
    }
}
