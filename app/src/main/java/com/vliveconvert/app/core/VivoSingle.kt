package com.vliveconvert.app.core

import java.io.File

/**
 * vivo 单文件实况照片写出。
 * 逻辑取自 ZLivePhoto 项目的 VivoSinglePlugin.write + OppoPlugin.buildLpexPayload，
 * 经真机实测 + 二进制逆向确认可被 vivo 相册识别。
 *
 * vivo 相册「关闭实况」时会把双文件（IMG_xxx.jpg + IMG_xxx.mp4）合并为一个 jpg：
 * 结构 = JPG 主体（Primary + GainMap）+ MP4（保留 vivoMediaEStream 实况标识、剥 vivoMediaExtInfo
 * 源 footer 包装）+ lpex box + convert footer，
 * XMP 使用 Google Container（含 MotionPhoto 视频项）并附带 VCamera 私有字段。
 *
 * 识别关键（真机实测确认）：vivo 相册同时依赖 vivoMediaEStream uuid box、
 * lpex box 与 convert footer 三者；缺 lpex 或保留 vivoMediaExtInfo 均会导致不被识别。
 */
internal object VivoSingle {

    /**
     * 写出 vivo 单文件实况（MotionPhoto="1"），返回输出文件路径（$stem.jpg）。
     */
    fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit
    ): String {
        // ── 1. 处理源视频：剥 vivoMediaExtInfo（源 footer 包装），保留 vivoMediaEStream ──
        // vivo 双文件 mp4 尾部布局：
        //   [ftyp…mdat][vivoMediaEStream uuid 138B][vivoMediaExtInfo uuid 2691B(内嵌源 cameralbum footer)]
        // 两个 uuid box 性质完全不同：
        //   - vivoMediaEStream：vivo 相册识别「实况视频」的关键标识 → 必须保留
        //   - vivoMediaExtInfo：其内容即源 cameralbum footer（双文件 mp4 自带的旧 footer）→ 必须剥掉，
        //     否则视频段会内嵌一个 cameralbum footer，与尾部 convert footer 重复，破坏识别。
        var video = Mp4Util.stripVivoUuid(asset.videoMp4)

        // ── 2. 插入 lpex (LivePhotoExtension) box 到 moov ──
        // 实测「可被 vivo 相册识别」的输出视频流里带 lpex box；缺 lpex → 不被识别。
        if (video.size >= 8) {
            val searchEnd = minOf(65536, video.size)
            val lpexMarker = byteArrayOf(0x6C, 0x70, 0x65, 0x78) // "lpex"
            if (BinaryUtils.indexOf(video.copyOfRange(0, searchEnd), lpexMarker) < 0) {
                try {
                    video = Mp4Util.insertBoxIntoMoov(video, "lpex", buildLpexPayload(asset))
                    log("info", "已合成 lpex box（LivePhotoExtension）插入 moov", "vivo")
                } catch (ex: Exception) {
                    log("warning", "lpex 合成失败，跳过（不影响播放）：${ex.message}", "vivo")
                }
            }
        }

        // ── 3. vivo 相册专属 convert footer（基础字段逐字对齐「可被识别」的输出） ──
        // 并合并源双文件 footer 的附加字段（人像: moduleid/portrait、joint.refocus、
        // refocusAlgoSource 等），相册据此保留人像徽标与光圈/虚化后编辑能力。
        val imageTime = asset.effectiveImageTime()
        val footerFields = linkedMapOf<String, Any?>(
            "com.vivo.gallery.livePhoto.otherPhone.MotionRotationOffset" to 0,
            "com.android.camera.imageTime" to imageTime,
            "com.vivo.gallery.file.convert" to 10004,
            "com.vivo.gallery.livePhoto.otherPhone.MotionRotationCheck" to 1,
            "com.android.camera.livephoto" to FooterUtil.oppoFixedId,
            "version" to 2200
        )
        for (src in listOf(asset.extras["vivo_jpg_footer"], asset.extras["vivo_mp4_footer"])) {
            (src as? Map<*, *>)?.forEach { (k, v) ->
                val key = k as? String ?: return@forEach
                // 仅跳过被单文件格式固定覆盖的键；其余（含嵌套 faceInfo 等）全部透传
                if (key !in footerFields && v != null) footerFields[key] = v
            }
        }
        val footerJson = FooterUtil.buildFooterJson(footerFields)
        val footer = FooterUtil.buildFooter(footerJson, FooterUtil.oppoFixedId, FooterUtil.extPrefix)

        // ── 4. XMP：视频项 Item:Length = video + footer（已被验证可识别） ──
        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildVivoSingleXmp(pts, asset.gainmapLength, video.size + footer.size)
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        // ── 5. 拼装输出：[JPEG+XMP][GainMap][streamdata][video(含 lpex)][convert footer] ──
        // streamdata 附加块紧跟图像数据（与双文件中的位置一致）：普通实况为流信息，
        // 人像实况为深度/虚化数据流，相册靠它保留人像徽标与光圈/虚化后编辑。
        val streamData = asset.extras["vivo_streamdata"] as? ByteArray ?: ByteArray(0)
        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(primary.size + gainmapLen + streamData.size + video.size + footer.size)
        var pos = 0
        System.arraycopy(primary, 0, output, pos, primary.size); pos += primary.size
        asset.gainmapJpeg?.let { System.arraycopy(it, 0, output, pos, it.size); pos += it.size }
        if (streamData.isNotEmpty()) {
            System.arraycopy(streamData, 0, output, pos, streamData.size); pos += streamData.size
        }
        System.arraycopy(video, 0, output, pos, video.size); pos += video.size
        System.arraycopy(footer, 0, output, pos, footer.size)

        // vivo 相册自己的合并文件不带 _MP 后缀，保持一致
        val outPath = File(outDir, "$stem.jpg").path
        File(outDir).mkdirs()
        File(outPath).writeBytes(output)
        log("info", "写出 vivo 单文件实况：${File(outPath).name}" +
            "（图像 ${primary.size}B + 视频 ${video.size}B（含 lpex）" +
            (if (streamData.isNotEmpty()) " + streamdata ${streamData.size}B" else "") +
            " + footer ${footer.size}B）", "vivo")
        return outPath
    }

    /**
     * 合成 lpex (LivePhotoExtension) box 载荷（vivo/OPPO 共用；字段逐字对齐可被相册识别的输出）。
     */
    fun buildLpexPayload(asset: LivePhotoAsset): ByteArray {
        val vi = asset.videoInfo
        val vw = (vi["width"] as? Int) ?: 0
        val vh = (vi["height"] as? Int) ?: 0
        val (iw, ih) = JpegUtil.getDimensions(asset.primaryJpeg)

        val payload = linkedMapOf<String, Any?>(
            "coverFramePts" to asset.effectivePtsUs(),
            "cropRect" to intArrayOf(0, 0, vw, vh),
            "desc" to "OppoMotionVideoExt",
            "matrixCount" to 0,
            "originPhotoSize" to intArrayOf(iw, ih),
            "photoCropFactor" to 1.0,
            "photoCropMatrix" to doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            "photoCropRect" to intArrayOf(0, 0, iw, ih),
            "photoEisCropFactor" to doubleArrayOf(1.0, 1.0),
            "photoEisMatrix" to doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            "subVideoScaleFactor" to 0.5,
            "version" to 1,
            "videoOrientation" to ((vi["rotation"] as? Int) ?: 0),
            "videoSize" to intArrayOf(vw, vh)
        )
        val jsonBytes = FooterUtil.buildFooterJson(payload)
        val prefix = "LivePhotoExtension".toByteArray(Charsets.US_ASCII)
        return prefix + jsonBytes
    }
}
