package com.vliveconvert.app

import com.vliveconvert.app.convert.Converter
import com.vliveconvert.app.core.BinaryUtils
import com.vliveconvert.app.core.FooterUtil
import com.vliveconvert.app.core.JpegUtil
import com.vliveconvert.app.core.Mp4Util
import com.vliveconvert.app.core.VivoDual
import com.vliveconvert.app.core.VivoSingle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 真机样本回归测试（vivo X200 Ultra 传输样本）。
 * 样本缺失时自动跳过，不影响其他环境。
 * 样本目录：C:/Users/brova/Downloads/vivo办公套件/
 * - IMG_20260831_134354 普通实况（曾可识别）
 * - IMG_20260831_134432 人像实况（footer tail 55B，曾不被识别）
 */
class RealSampleRegressionTest {

    private val sampleDir = "C:/Users/brova/Downloads/vivo办公套件"

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
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

    private fun convertAndVerify(stem: String, expectedImageTime: Long, expectPortrait: Boolean = false) {
        val jpg = File("$sampleDir/$stem.jpg")
        val mp4 = File("$sampleDir/$stem.mp4")
        assumeTrue("样本缺失，跳过", jpg.exists() && mp4.exists())

        assertTrue("$stem 应识别为双文件实况", VivoDual.isVivoDualFile(jpg.absolutePath))

        val outDir = Files.createTempDirectory("vlc_real_$stem").toFile()
        val outPath = Converter.convertToVivoSingle(jpg.absolutePath, outDir.absolutePath, ::log)
        val data = File(outPath).readBytes()
        println("REAL_OUTPUT: $outPath (${data.size}B)")

        // XMP 为 vivo 单文件实况标记
        val (jpegs, _) = JpegUtil.splitJpegs(data)
        val xmp = JpegUtil.findXmpSegment(jpegs[0])
        assertNotNull(xmp)
        assertTrue(xmp!!.xmpText.contains("""GCamera:MotionPhoto="1""""))
        assertTrue(xmp.xmpText.contains("ns.vivo.com/photos"))

        // convert footer：固定 ID + imageTime 透传
        val footer = FooterUtil.parseFooter(data)
        assertNotNull(footer)
        assertEquals(FooterUtil.oppoFixedId, footer!!.livephotoId)
        assertEquals(expectedImageTime, footer.imageTime)

        // streamdata 附加块透传（普通实况 114B 流信息；人像实况约 4MB 深度/虚化数据，
        // 丢失会导致相册不显示人像徽标、无法后编辑光圈/虚化）
        assertTrue("streamdata 附加块应透传",
            countOf(data, "streamdata".toByteArray(Charsets.US_ASCII)) >= 1)

        if (expectPortrait) {
            // 人像标记从源 footer 合并进 convert footer
            assertEquals("moduleid=portrait 应透传",
                "portrait", footer.json?.get("com.android.camera.moduleid"))
            assertNotNull("joint.refocus 应透传",
                footer.json?.get("com.android.camera.joint.refocus"))
        }

        // X200 Ultra 双文件 MP4 不含 vivoMediaEStream box（旧 iQOO 格式才有），
        // 识别核心为 lpex + convert footer + XMP（ZLivePhoto 验证过的 OPPO 输出同样无 EStream）
        // ExtInfo 包装剥离：输出中仅剩 convert footer 的 extPrefix 1 处
        assertEquals(1, countOf(data, "vivoMediaExtInfovivo".toByteArray(Charsets.US_ASCII)))
        // lpex 已插入
        assertTrue(countOf(data, "LivePhotoExtension".toByteArray(Charsets.US_ASCII)) >= 1)
    }

    @Test
    fun normalLiveSample() = convertAndVerify("IMG_20260831_134354", 0L)

    @Test
    fun portraitLiveSample() = convertAndVerify("IMG_20260831_134432", 31L, expectPortrait = true)

    /**
     * 字节完整性审计：逐区域核对「源双文件 → 输出单文件」没有任何数据被漏掉。
     * 1) 区域存在性：主图（XMP 前/后段）、增益图、streamdata、视频流切片均能在输出中找到
     * 2) 大小守恒：输出大小 = 新主图 + 增益图 + streamdata + 视频流 + convert footer（逐字节对账）
     * 3) footer 字段审计：源 JPG/MP4 footer 的全部键都应出现在输出 footer
     *    （仅 livephoto/version 被单文件格式固定值覆盖属预期）
     */
    @Test
    fun byteCompletenessAudit() {
        for ((stem, expectPortrait) in listOf(
            "IMG_20260831_134354" to false,
            "IMG_20260831_134432" to true
        )) {
            val jpg = File("$sampleDir/$stem.jpg")
            val mp4 = File("$sampleDir/$stem.mp4")
            assumeTrue("样本缺失，跳过", jpg.exists() && mp4.exists())

            // ── 源区域划分（与 VivoDual.read 相同逻辑）──
            val src = jpg.readBytes()
            val srcFooter = FooterUtil.parseFooter(src)!!
            val body = src.copyOfRange(0, srcFooter.footerStart)
            val (jpegs, consumed) = JpegUtil.splitJpegs(body)
            val primary = jpegs[0]
            val gainmap = if (jpegs.size > 1) jpegs[1] else ByteArray(0)
            val streamData = if (consumed < body.size) body.copyOfRange(consumed, body.size) else ByteArray(0)
            val video = Mp4Util.stripVivoUuid(mp4.readBytes())
            val mp4Footer = FooterUtil.parseFooter(mp4.readBytes())

            // ── 转换 ──
            val outDir = Files.createTempDirectory("vlc_audit_$stem").toFile()
            val outPath = Converter.convertToVivoSingle(jpg.absolutePath, outDir.absolutePath, ::log)
            val data = File(outPath).readBytes()

            // ── 2) 视频段预期结果（write 会对无 lpex 的源插入 lpex box，moov 尺寸字段随之变化）──
            val asset = VivoDual.read(jpg.absolutePath, ::log)
            val expectedVideo = try {
                val searchEnd = minOf(65536, video.size)
                if (video.size >= 8 &&
                    BinaryUtils.indexOf(video.copyOfRange(0, searchEnd), "lpex".toByteArray()) < 0
                ) {
                    Mp4Util.insertBoxIntoMoov(video, "lpex", VivoSingle.buildLpexPayload(asset))
                } else {
                    video
                }
            } catch (e: Exception) {
                video
            }

            // ── 1) 区域存在性 ──
            val oldXmp = JpegUtil.findXmpSegment(primary)!!
            val newXmp = JpegUtil.findXmpSegment(data)!!
            // 主图 XMP 之前（SOI + Exif 段）应原样在输出开头
            val preXmp = primary.copyOfRange(0, oldXmp.segStart)
            assertTrue("$stem: 主图 XMP 前段应在输出开头",
                data.size >= preXmp.size && preXmp.contentEquals(data.copyOfRange(0, preXmp.size)))
            // 主图 XMP 之后（MPF/ICC/DQT/SOF…）应原样跟随新 XMP
            val postXmp = primary.copyOfRange(oldXmp.segStart + oldXmp.totalLen, primary.size)
            assertTrue("$stem: 主图 XMP 后段应跟随新 XMP",
                BinaryUtils.indexOf(data, postXmp.copyOfRange(0, 64)) >= 0)
            // 视频流用「插入 lpex 后的预期结果」做切片（源视频头部含 moov 尺寸字段会因插入而变化）
            for ((name, region) in listOf(
                "增益图" to gainmap, "streamdata" to streamData, "视频流(含lpex)" to expectedVideo
            )) {
                for ((i, s) in slices(region).withIndex()) {
                    assertTrue("$stem: $name 切片$i 应存在于输出", BinaryUtils.indexOf(data, s) >= 0)
                }
            }

            // ── 3) 大小守恒（逐字节对账：任何未计入区域都会导致不等） ──
            val newPrimaryLen = primary.size - oldXmp.totalLen + newXmp.totalLen
            val outFooter = FooterUtil.parseFooter(data)!!
            val footerLen = data.size - outFooter.footerStart
            val expected = newPrimaryLen + gainmap.size + streamData.size + expectedVideo.size + footerLen
            assertEquals("$stem: 输出大小应与全部源区域之和一致（无字节丢失）",
                expected, data.size)

            // ── 3) footer 字段审计 ──
            val srcKeys = LinkedHashSet<String>()
            srcFooter.json?.keys?.let { srcKeys.addAll(it) }
            mp4Footer?.json?.keys?.let { srcKeys.addAll(it) }
            val outKeys = outFooter.json!!.keys
            // livephoto/version 被单文件格式固定值覆盖（oppoFixedId / 2200）属预期
            val expectedMissing = setOf("com.android.camera.livephoto", "version")
            val missing = srcKeys.filter { it !in outKeys && it !in expectedMissing }
            assertTrue("$stem: footer 字段仍有丢失: $missing", missing.isEmpty())
            if (expectPortrait) {
                assertEquals("moduleid=portrait 应透传",
                    "portrait", outFooter.json?.get("com.android.camera.moduleid"))
            }
            println("AUDIT[$stem]: regions ok, size=${data.size}, footer keys ${srcKeys.size}->${outKeys.size} all preserved")
        }
    }

    /** 大区域用头/中/尾 64B 切片做存在性检查（避免大字节数组朴素查找过慢） */
    private fun slices(region: ByteArray): List<ByteArray> {
        if (region.size < 64) return if (region.isEmpty()) emptyList() else listOf(region)
        return listOf(0, region.size / 2, region.size - 64)
            .map { it.coerceIn(0, region.size - 64) }
            .map { region.copyOfRange(it, it + 64) }
            .distinct()
    }
}
