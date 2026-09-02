package com.vliveconvert.app

import com.vliveconvert.app.convert.Converter
import com.vliveconvert.app.core.BinaryUtils
import com.vliveconvert.app.core.FooterUtil
import com.vliveconvert.app.core.JpegUtil
import com.vliveconvert.app.core.VivoDual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 转换管线的 JVM 回归测试：
 * 用字节级合成的 vivo 双文件样本（IMG_xxx.jpg + IMG_xxx.mp4）跑完整
 * detect → read → write 流程，校验单文件输出的关键结构。
 */
class ConverterPipelineTest {

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
    }

    // ------------------------------------------------ 合成样本构造

    /** box = 4B size + 4B type + payload */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val b = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(b, 0, (payload.size + 8).toLong())
        val t = type.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(t, 0, b, 4, 4)
        System.arraycopy(payload, 0, b, 8, payload.size)
        return b
    }

    /** 最小合法 JPEG：SOI + SOF0(16x16) + SOS + 熵数据（无 FF）+ EOI */
    private fun minimalJpeg(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
        // SOF0：len=11, 精度8, 高16, 宽16, 分量数1
        run {
            val payload = byteArrayOf(
                8, 0, 16, 0, 16, 1,
                1, 0x11, 0
            )
            val seg = ByteArray(2 + 2 + payload.size) // [FF C0][len][payload]
            seg[0] = 0xFF.toByte()
            seg[1] = 0xC0.toByte()
            BinaryUtils.writeU16BE(seg, 2, payload.size + 2)
            System.arraycopy(payload, 0, seg, 4, payload.size)
            out.write(seg)
        }
        // SOS：len=8
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0))
        // 熵编码数据（不含 FF）
        out.write(byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x12, 0x34, 0x56))
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // EOI
        return out.toByteArray()
    }

    /** 伪随机（可复现）字节块，用于模拟 mdat 视频数据 */
    private fun pseudoRandom(size: Int, seed: Int): ByteArray {
        val b = ByteArray(size)
        var s = seed.toLong()
        for (i in 0 until size) {
            s = s * 6364136223846793005L + 1442695040888963407L
            b[i] = (s ushr 33).toByte()
        }
        // 保证不含连续合法 box 结构造成遍历歧义——不需要，mdat 是不透明载荷
        return b
    }

    /** 合成 vivo 双文件 MP4：ftyp + mdat + moov + vivoMediaEStream uuid + vivoMediaExtInfo uuid */
    private fun vivoDualMp4(): ByteArray {
        val ftyp = box("ftyp", "isom".toByteArray() +
            byteArrayOf(0, 0, 2, 0) + "isom".toByteArray() + "mp41".toByteArray())
        val mdatPayload = pseudoRandom(2048, 42)
        val mdat = box("mdat", mdatPayload)
        val moov = box("moov", ByteArray(0))
        val eStream = box("uuid", "vivoMediaEStream".toByteArray(Charsets.US_ASCII) +
            pseudoRandom(122, 7)) // 共 138B，对齐真机布局
        val extInfoJson = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.imageTime" to 12L,
            "com.android.camera.livephoto" to TEST_LIVE_ID,
            "version" to 2107
        ))
        val extInfoFooter = FooterUtil.buildFooter(extInfoJson, TEST_LIVE_ID, FooterUtil.vivoPrefix)
        val extInfo = box("uuid", "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII) + extInfoFooter)
        return ftyp + mdat + moov + eStream + extInfo
    }

    /** 合成 streamdata 附加块（vivo "streamdata" 魔数 + 类型 + 伪随机载荷） */
    private fun fakeStreamData(type: String, size: Int, seed: Int): ByteArray =
        "streamdata".toByteArray(Charsets.US_ASCII) +
            type.toByteArray(Charsets.US_ASCII) +
            pseudoRandom(size, seed)

    /** 合成 vivo 双文件 JPG：最小 JPEG + streamdata 附加块 + "vivo" 前缀 + footer JSON + cameralbum footer */
    private fun vivoDualJpeg(imageTime: Long = 12): ByteArray {
        val json = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.imageTime" to imageTime,
            "com.android.camera.livephoto" to TEST_LIVE_ID,
            "version" to 2107
        ))
        val footer = FooterUtil.buildFooter(json, TEST_LIVE_ID, FooterUtil.vivoPrefix)
        return minimalJpeg() + fakeStreamData("DEGS", 64, 11) + FooterUtil.vivoPrefix + footer
    }

    /**
     * 构造变长 ID 字段的 footer（镜像 vivo X200 Ultra 人像实况实测字节）：
     * ID 字段 = 11 个 0x00 + '/' + 28 字符 ID（共 40B），tail = 40+4+11 = 55B。
     */
    private fun buildPortraitFooter(jsonBytes: ByteArray, id: String, prefix: ByteArray): ByteArray {
        val idField = ByteArray(40)
        idField[11] = '/'.code.toByte()
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        System.arraycopy(idBytes, 0, idField, 12, 28)
        val tail = ByteArray(40 + 4 + FooterUtil.magic.size)
        System.arraycopy(idField, 0, tail, 0, 40)
        tail[40] = 0xFF.toByte(); tail[41] = 0xFF.toByte(); tail[42] = 0xFF.toByte(); tail[43] = 0xFF.toByte()
        System.arraycopy(FooterUtil.magic, 0, tail, 44, FooterUtil.magic.size)

        val out = java.io.ByteArrayOutputStream()
        out.write(prefix)
        out.write(jsonBytes)
        val lenBuf = ByteArray(4)
        BinaryUtils.writeU32BE(lenBuf, 0, jsonBytes.size.toLong())
        out.write(lenBuf)
        out.write(FooterUtil.marker)
        BinaryUtils.writeU32BE(lenBuf, 0, (tail.size + 4).toLong())
        out.write(lenBuf)
        out.write(tail)
        return out.toByteArray()
    }

    /** 合成 vivo 人像实况双文件（version 2202 + refocus 字段、JPG JSON 无 imageTime、55B 尾部） */
    private fun makePortraitPair(dir: File, stem: String = "IMG_5001"): Pair<File, File> {
        val PORTRAIT_ID = "1788155072103343df5100000000"
        val jpgJson = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.takenmodel" to "vivo X200 Ultra",
            "com.android.camera.camerafacing" to "0",
            "com.android.camera.joint.refocusAlgoSource" to 2,
            "com.android.camera.livephoto" to PORTRAIT_ID,
            "version" to 2202,
            "com.android.camera.joint.refocus" to 2204
        ))
        val jpg = File(dir, "$stem.jpg").apply {
            writeBytes(minimalJpeg() + fakeStreamData("IAC", 512, 13) + FooterUtil.vivoPrefix +
                buildPortraitFooter(jpgJson, PORTRAIT_ID, FooterUtil.vivoPrefix))
        }
        val mp4Json = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.takenmodel" to "vivo X200 Ultra",
            "com.android.camera.camerafacing" to "0",
            "com.android.camera.imageTime" to 31L,
            "com.android.camera.moduleid" to "portrait",
            "com.android.camera.livephoto" to PORTRAIT_ID,
            "version" to 2200
        ))
        val extInfo = box("uuid", "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII) +
            buildPortraitFooter(mp4Json, PORTRAIT_ID, FooterUtil.extPrefix))
        val mp4 = File(dir, "$stem.mp4").apply {
            writeBytes(box("ftyp", "isom".toByteArray() +
                byteArrayOf(0, 0, 2, 0) + "isom".toByteArray() + "mp41".toByteArray()) +
                box("mdat", pseudoRandom(2048, 99)) +
                box("moov", ByteArray(0)) + extInfo)
        }
        return jpg to mp4
    }

    private fun makePair(dir: File, stem: String = "IMG_1001"): Pair<File, File> {
        val jpg = File(dir, "$stem.jpg").apply { writeBytes(vivoDualJpeg()) }
        val mp4 = File(dir, "$stem.mp4").apply { writeBytes(vivoDualMp4()) }
        return jpg to mp4
    }

    private fun countOf(data: ByteArray, target: ByteArray): Int {
        var n = 0
        var i = BinaryUtils.indexOf(data, target)
        while (i >= 0) {
            n++
            i = BinaryUtils.indexOf(data, target, i + 1)
        }
        return n
    }

    // ------------------------------------------------ 测试

    @Test
    fun dualFileDetected() {
        val dir = Files.createTempDirectory("vlc_dual").toFile()
        val (jpg, _) = makePair(dir)
        assertTrue("成对的 vivo 双文件应被识别", VivoDual.isVivoDualFile(jpg.absolutePath))
    }

    @Test
    fun jpgWithoutSiblingMp4Rejected() {
        val dir = Files.createTempDirectory("vlc_nosib").toFile()
        val jpg = File(dir, "IMG_2001.jpg").apply { writeBytes(vivoDualJpeg()) }
        assertFalse("缺少伴生 .mp4 不应识别", VivoDual.isVivoDualFile(jpg.absolutePath))
    }

    @Test
    fun plainJpgWithMp4Rejected() {
        val dir = Files.createTempDirectory("vlc_plain").toFile()
        val jpg = File(dir, "IMG_2002.jpg").apply { writeBytes(minimalJpeg()) }
        val mp4 = File(dir, "IMG_2002.mp4").apply { writeBytes(vivoDualMp4()) }
        assertFalse("无 vivo footer 的普通 JPG+MP4 不应识别",
            VivoDual.isVivoDualFile(jpg.absolutePath))
    }

    @Test
    fun embeddedMotionJpgRejected() {
        // Google 内嵌式动态照片（XMP 含 MotionPhoto="1"）即使带同名 mp4 也应排除
        val dir = Files.createTempDirectory("vlc_embed").toFile()
        val xmpJpeg = JpegUtil.replaceOrInsertXmp(
            minimalJpeg(),
            """<x:xmpmeta><rdf:RDF GCamera:MotionPhoto="1"/></x:xmpmeta>""")
        val jpg = File(dir, "IMG_2003.jpg").apply { writeBytes(xmpJpeg) }
        File(dir, "IMG_2003.mp4").apply { writeBytes(vivoDualMp4()) }
        assertFalse("内嵌式动态照片不应识别为双文件", VivoDual.isVivoDualFile(jpg.absolutePath))
    }

    @Test
    fun convertDualToSingleProducesValidStructure() {
        val dir = Files.createTempDirectory("vlc_conv").toFile()
        val (jpg, _) = makePair(dir, "IMG_3001")
        val outDir = Files.createTempDirectory("vlc_out").toFile()

        val outPath = Converter.convertToVivoSingle(jpg.absolutePath, outDir.absolutePath, ::log)
        val out = File(outPath)
        assertTrue("输出文件应存在", out.exists())
        assertEquals("输出名应与源同名（vivo 相册合并产物不带后缀）",
            "IMG_3001.jpg", out.name)

        val data = out.readBytes()

        // 1. JPEG 主体合法且 XMP 为 vivo 单文件实况标记
        val (jpegs, consumed) = JpegUtil.splitJpegs(data)
        assertEquals("输出应含 1 个完整 JPEG 主体", 1, jpegs.size)
        val xmp = JpegUtil.findXmpSegment(jpegs[0])
        assertNotNull("输出应含 XMP APP1 段", xmp)
        xmp!!.xmpText.let {
            assertTrue(it.contains("""GCamera:MotionPhoto="1""""))
            assertTrue(it.contains("ns.vivo.com/photos"))
            assertTrue(it.contains("VCamera:VMediaKitVersion"))
        }

        // 2. 尾部 convert footer：固定 ID + 源 imageTime 透传
        val footer = FooterUtil.parseFooter(data)
        assertNotNull("输出尾部应含 cameralbum! footer", footer)
        assertEquals(FooterUtil.oppoFixedId, footer!!.livephotoId)
        assertEquals("源 footer 的 imageTime 应透传到 convert footer",
            12L, footer.imageTime)

        // 3. vivoMediaEStream 保留（vivo 相册识别实况的关键）且仅 1 处
        val eStreamTag = "vivoMediaEStream".toByteArray(Charsets.US_ASCII)
        assertEquals("应保留 1 个 vivoMediaEStream uuid box", 1, countOf(data, eStreamTag))

        // 4. 源 MP4 的 vivoMediaExtInfo uuid box 已剥离（只剩 convert footer 的 extPrefix）
        val extInfoTag = "vivoMediaExtInfovivo".toByteArray(Charsets.US_ASCII)
        assertEquals("vivoMediaExtInfo 包装应被剥离", 1, countOf(data, extInfoTag))

        // 5. lpex box 已插入 moov
        assertTrue("应含 lpex box（LivePhotoExtension 载荷）",
            countOf(data, "LivePhotoExtension".toByteArray(Charsets.US_ASCII)) == 1)

        // 6. 视频数据无损透传（mdat 载荷逐字节保留）
        val mdatSlice = pseudoRandom(2048, 42).copyOfRange(100, 164)
        assertTrue("mdat 视频载荷应无损保留", BinaryUtils.indexOf(data, mdatSlice) >= 0)

        // 8. streamdata 附加块原样透传（紧跟图像数据之后）
        assertTrue("streamdata 附加块应透传",
            BinaryUtils.indexOf(data, fakeStreamData("DEGS", 64, 11)) >= 0)

        // 7. 修改时间保留
        assertEquals("输出修改时间应继承源文件",
            jpg.lastModified() / 1000, out.lastModified() / 1000)
    }

    @Test
    fun portraitDualDetectedAndConverted() {
        // vivo X200 Ultra 人像实况：footer tail 55B（ID 字段 40B，含二进制前缀）
        val dir = Files.createTempDirectory("vlc_portrait").toFile()
        val (jpg, _) = makePortraitPair(dir)
        assertTrue("人像实况应被识别为双文件", VivoDual.isVivoDualFile(jpg.absolutePath))

        val outDir = Files.createTempDirectory("vlc_portrait_out").toFile()
        val outPath = Converter.convertToVivoSingle(jpg.absolutePath, outDir.absolutePath, ::log)
        val data = File(outPath).readBytes()

        val footer = FooterUtil.parseFooter(data)
        assertNotNull("输出尾部应含 convert footer", footer)
        // JPG JSON 无 imageTime → 应从伴生 MP4 footer 透传（=31）
        assertEquals("imageTime 应取自 MP4 footer", 31L, footer!!.imageTime)
        assertEquals(FooterUtil.oppoFixedId, footer.livephotoId)

        // 人像标记透传：moduleid（来自 MP4 footer）+ refocus 字段（来自 JPG footer）
        assertEquals("moduleid=portrait 应合并进 convert footer",
            "portrait", footer.json?.get("com.android.camera.moduleid"))
        assertEquals("joint.refocus 应合并进 convert footer",
            2204, footer.json?.get("com.android.camera.joint.refocus"))
        assertEquals("refocusAlgoSource 应合并进 convert footer",
            2, footer.json?.get("com.android.camera.joint.refocusAlgoSource"))

        // 人像深度/虚化数据流（streamdata 附加块）原样透传
        assertTrue("人像 streamdata 深度块应透传",
            BinaryUtils.indexOf(data, fakeStreamData("IAC", 512, 13)) >= 0)
    }

    @Test
    fun convertRejectsNonDualFile() {
        val dir = Files.createTempDirectory("vlc_rej").toFile()
        val jpg = File(dir, "IMG_4001.jpg").apply { writeBytes(minimalJpeg()) }
        try {
            Converter.convertToVivoSingle(jpg.absolutePath, dir.absolutePath, ::log)
            throw AssertionError("非双文件应抛出异常")
        } catch (e: Exception) {
            assertTrue("异常应为「不是 vivo 双文件实况照片」: ${e.message}",
                (e.message ?: "").contains("不是 vivo 双文件实况照片"))
        }
    }

    @Test
    fun footerJsonRoundTripWithNestedValues() {
        // footer 字段合并的完整性：嵌套对象/数组/null 均应无损往返
        // （vivo 人像 footer 中的 faceInfo 即嵌套对象）
        val json = """{"a":"x","b":1,"c":2.5,"d":true,"e":null,"f":{"n":1,"s":"y"},"g":[1,2,3],"h":["z"]}"""
        val parsed = com.vliveconvert.app.core.JsonMin.parse(json.toByteArray(Charsets.UTF_8))
        assertNotNull(parsed)
        val rebuilt = FooterUtil.buildFooterJson(parsed!!)
        val reparsed = com.vliveconvert.app.core.JsonMin.parse(rebuilt)
        assertEquals("嵌套 JSON 往返应无损", parsed, reparsed)
    }

    companion object {
        /** 28 字符 livephoto ID（'-<数字>' + '0' 填充） */
        private val TEST_LIVE_ID: String = "-1234567890".padEnd(28, '0')
    }
}
