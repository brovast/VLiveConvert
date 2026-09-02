package com.vliveconvert.app.core

import java.io.File
import java.io.FileInputStream

/**
 * XMP 模板与源 XMP 字段提取。
 * vivo 单文件实况模板逐字来自 vivo 相册「关闭实况」合并产物的真实样本
 * （取自 ZLivePhoto 项目，经真机验证可被 vivo 相册识别为实况照片），
 * 保证转换输出与原生格式的 XMP 结构、属性顺序、缩进完全一致。
 */
internal object XmpTemplate {

    // ------------------------------------------------ vivo 单文件实况模板
    // 结构 = Google 容器（Primary + GainMap + MotionPhoto 视频项）+ VCamera 私有字段；
    // vivo 相册识别单文件实况的关键是 GCamera:MotionPhoto="1"（="0" 即关闭实况状态）

    private const val VivoSingleHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9">
"""
    private const val VivoSingleHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9">
"""
    private const val VivoSingleItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="Primary"
              Item:Mime="image/jpeg"/>
          </rdf:li>
"""
    private const val VivoSingleItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="GainMap"
              Item:Mime="image/jpeg"
              Item:Length="{gainmap_len}"/>
          </rdf:li>
"""
    private const val VivoSingleItemVideo: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
"""
    private const val VivoSingleTail: String = """
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>"""

    /** vivo 单文件实况：Google 容器 + VCamera 私有字段，MotionPhoto 恒为 1。 */
    fun buildVivoSingleXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        val head = if (gainmapLen != null) VivoSingleHeadHdr else VivoSingleHeadNonHdr
        val parts = mutableListOf(head.replace("{pts}", ptsUs.toString()), VivoSingleItemPrimary)
        if (gainmapLen != null) {
            parts.add(VivoSingleItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(VivoSingleItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(VivoSingleTail)
        return parts.joinToString("")
    }

    // ------------------------------------------------ 解析（识别用）

    internal class MotionXmpInfo {
        var isMotion: Boolean = false
    }

    private val motionPhotoRegex = Regex("""GCamera:MotionPhoto="(\d+)"""")
    private val microVideoRegex = Regex("""GCamera:MicroVideo="(\d+)"""")

    /**
     * 判断 XMP 是否为内嵌式动态照片标记（Google MotionPhoto / 旧版 MicroVideo）。
     * vivo 双文件的 XMP 不含这些标记，据此把双文件与内嵌单文件区分开。
     */
    fun parseMotionXmp(xmpText: String): MotionXmpInfo {
        val info = MotionXmpInfo()
        if (xmpText.isEmpty()) return info

        val motionMatch = motionPhotoRegex.find(xmpText)
        if (motionMatch != null && motionMatch.groupValues[1] == "1") info.isMotion = true

        val microMatch = microVideoRegex.find(xmpText)
        if (microMatch != null && microMatch.groupValues[1] == "1") info.isMotion = true

        return info
    }

    /**
     * 从文件头部直接定位 XMP 文本（检测用，容忍截断）。
     * 不依赖固定头部窗口：先按 XMP APP1 前缀定位，再找闭合标记。
     */
    fun sniffXmp(path: String, limit: Int = 2 * 1024 * 1024): String {
        return try {
            val f = File(path)
            val fileSize = f.length().toInt()
            val bufLen = minOf(limit, fileSize)
            if (bufLen < 2) return ""
            val buffer = ByteArray(bufLen)
            FileInputStream(path).use { fis ->
                var read = 0
                while (read < bufLen) {
                    val n = fis.read(buffer, read, bufLen - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 2 || buffer[0] != 0xFF.toByte() || buffer[1] != 0xD8.toByte()) return@use ""
                val head = buffer.copyOfRange(0, read)
                val idx = BinaryUtils.indexOf(head, JpegUtil.xmpApp1Prefix)
                if (idx == -1) return@use ""
                val endMarker = "</x:xmpmeta>".toByteArray(Charsets.US_ASCII)
                val tail = head.copyOfRange(idx, head.size)
                val end = BinaryUtils.indexOf(tail, endMarker)
                if (end == -1) return@use ""
                val length = idx + end + 12
                return@use String(head.copyOfRange(idx, idx + length), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }
}
