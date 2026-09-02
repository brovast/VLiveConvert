package com.vliveconvert.app.core

/**
 * MP4 (ISOBMFF) box 级工具：box 遍历、vivo uuid 处理、
 * lpex box 插入（含 stco/co64 偏移修复）、视频轨信息解析。
 * 纯字节级操作，不重编码。
 */
internal class Mp4Exception(message: String) : Exception(message)

internal object Mp4Util {
    val vivoUuid: ByteArray = "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII) // 16 字节

    internal class Box(val type: String, val offset: Int, val size: Int, val headerLen: Int)

    /**
     * 遍历 [start, end) 区间内的顶层 box，返回 Box 序列。
     * 遇到非法 box 即停止。box type 必须为 4 个可打印 ASCII。
     */
    fun iterateBoxes(data: ByteArray, start: Int, end: Int): Sequence<Box> = sequence {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = BinaryUtils.readU32BE(data, pos)

            // 校验 box type 为可打印 ASCII（OPPO 私有浮点块 type 非 ASCII，借此截断）
            var validType = true
            for (i in pos + 4 until pos + 8) {
                val c = data[i].toInt() and 0xFF
                if (c < 0x20 || c > 0x7E) { validType = false; break }
            }
            if (!validType) return@sequence

            var size = size32
            var header = 8
            if (size32 == 1L) {
                if (pos + 16 > end) return@sequence
                size = BinaryUtils.readU64BE(data, pos + 8)
                header = 16
            } else if (size32 == 0L) {
                size = (end - pos).toLong()
            }

            if (size < header || pos + size > end) return@sequence

            val typeStr = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            yield(Box(typeStr, pos, size.toInt(), header))
            pos += size.toInt()
        }
    }

    fun hasFtyp(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val ftyp = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        return BinaryUtils.arrayEquals(data, 4, ftyp)
    }

    /** 若 MP4 末尾存在 UUID 为 'vivoMediaExtInfo' 的 uuid box，则去除。 */
    fun stripVivoUuid(data: ByteArray): ByteArray {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        if (boxes.isEmpty()) return data

        val last = boxes.last()
        if (last.type == "uuid" && BinaryUtils.arrayEquals(data, last.offset + 8, vivoUuid)) {
            return data.copyOfRange(0, last.offset)
        }
        return data
    }

    private fun walkInto(
        data: ByteArray, offset: Int, size: Int, headerLen: Int, pathTypes: Set<String>
    ): Sequence<Box> = sequence {
        for (b in iterateBoxes(data, offset + headerLen, offset + size)) {
            yield(b)
            if (b.type in pathTypes) {
                yieldAll(walkInto(data, b.offset, b.size, b.headerLen, pathTypes))
            }
        }
    }

    /** 修复 moov 内所有 stco/co64 条目：位于 insertAt 之后的偏移整体后移 delta。 */
    private fun fixChunkOffsets(
        data: ByteArray, buf: ByteArray, moovOff: Int, moovSize: Int,
        insertAt: Int, delta: Int
    ) {
        val containers = setOf("trak", "mdia", "minf", "stbl")
        for (b in walkInto(data, moovOff, moovSize, 8, containers)) {
            if (b.type != "stco" && b.type != "co64") continue
            val entrySize = if (b.type == "co64") 8 else 4
            val body = b.offset + b.headerLen
            val count = BinaryUtils.readU32BE(data, body + 4).toInt()
            val entriesStart = body + 8

            for (i in 0 until count) {
                val epos = entriesStart + i * entrySize
                if (b.type == "co64") {
                    val v = BinaryUtils.readU64BE(data, epos)
                    if (v.toULong() >= insertAt.toULong()) {
                        BinaryUtils.writeU64BE(buf, epos, v + delta.toLong())
                    }
                } else {
                    val v = BinaryUtils.readU32BE(data, epos)
                    if (v >= insertAt) {
                        BinaryUtils.writeU32BE(buf, epos, v + delta)
                    }
                }
            }
        }
    }

    /** 把一个顶层自定义 box 追加为 moov 的最后一个子 box，并修复 stco/co64 偏移。 */
    fun insertBoxIntoMoov(data: ByteArray, boxType: String, payload: ByteArray): ByteArray {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" }
            ?: throw Mp4Exception("MP4 缺少 moov box")

        val moovOff = moov.offset
        val moovSize = moov.size
        val insertAt = moovOff + moovSize

        val newBox = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(newBox, 0, (payload.size + 8).toLong())
        val typeBytes = boxType.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, newBox, 4, typeBytes.size)
        System.arraycopy(payload, 0, newBox, 8, payload.size)
        val delta = newBox.size

        val buf = data.copyOf()
        fixChunkOffsets(data, buf, moovOff, moovSize, insertAt, delta)

        // 更新 moov size
        BinaryUtils.writeU32BE(buf, moovOff, (moovSize + delta).toLong())

        val result = ByteArray(buf.size + delta)
        System.arraycopy(buf, 0, result, 0, insertAt)
        System.arraycopy(newBox, 0, result, insertAt, delta)
        System.arraycopy(buf, insertAt, result, insertAt + delta, buf.size - insertAt)
        return result
    }

    /**
     * 解析主视频轨信息：codec/width/height/rotation/duration_us/fps/frame_count。
     */
    fun getTrackInfo(data: ByteArray): MutableMap<String, Any?>? {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return null

        val moovOff = moov.offset
        val moovSize = moov.size

        val info: MutableMap<String, Any?> = mutableMapOf(
            "codec" to "",
            "width" to 0,
            "height" to 0,
            "rotation" to 0,
            "duration_us" to -1L,
            "fps" to 0.0,
            "frame_count" to 0L
        )

        for (b in iterateBoxes(data, moovOff + 8, moovOff + moovSize)) {
            if (b.type != "trak") continue

            val trak = walkInto(data, b.offset, b.size, b.headerLen, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue

            // hdlr 为 FullBox：version/flags(4) + pre_defined(4) + handler_type(4)
            val hdlrBody = hdlr.offset + hdlr.headerLen
            val videBytes = byteArrayOf(0x76, 0x69, 0x64, 0x65) // "vide"
            if (!BinaryUtils.arrayEquals(data, hdlrBody + 8, videBytes)) continue

            // tkhd：宽高 = 末尾 8 字节（16.16 定点），旋转矩阵在其前 36 字节
            val tkhd = trak.firstOrNull { it.type == "tkhd" }
            if (tkhd != null) {
                val tkhdEnd = tkhd.offset + tkhd.size
                val w16 = BinaryUtils.readU32BE(data, tkhdEnd - 8)
                val h16 = BinaryUtils.readU32BE(data, tkhdEnd - 4)
                info["width"] = (w16 ushr 16).toInt()
                info["height"] = (h16 ushr 16).toInt()

                val m = tkhdEnd - 8 - 36
                val a = BinaryUtils.readI32BE(data, m)
                val b2 = BinaryUtils.readI32BE(data, m + 4)
                val c = BinaryUtils.readI32BE(data, m + 8)
                val d = BinaryUtils.readI32BE(data, m + 12)
                if (a == 0 && b2 == 65536 && c == -65536 && d == 0) info["rotation"] = 90
                else if (a == -65536 && b2 == 0 && c == 0 && d == -65536) info["rotation"] = 180
                else if (a == 0 && b2 == -65536 && c == 65536 && d == 0) info["rotation"] = 270
            }

            // mdhd：timescale + duration
            val mdhd = trak.firstOrNull { it.type == "mdhd" }
            var durationS = 0.0
            if (mdhd != null) {
                val body = mdhd.offset + mdhd.headerLen
                val version = data[body]
                val timescale: Long
                val duration: Long
                if (version.toInt() == 1) {
                    timescale = BinaryUtils.readU32BE(data, body + 12)
                    duration = BinaryUtils.readU64BE(data, body + 16)
                } else {
                    timescale = BinaryUtils.readU32BE(data, body + 12)
                    duration = BinaryUtils.readU32BE(data, body + 16)
                }
                if (timescale > 0) {
                    durationS = duration.toDouble() / timescale
                    info["duration_us"] = (durationS * 1_000_000).toLong()
                }
            }

            // stts：样本总数 → fps
            val stts = trak.firstOrNull { it.type == "stts" }
            if (stts != null) {
                val body = stts.offset + stts.headerLen
                val count = BinaryUtils.readU32BE(data, body + 4).toInt()
                var total = 0L
                for (i in 0 until count) {
                    total += BinaryUtils.readU32BE(data, body + 8 + i * 8)
                }
                info["frame_count"] = total
                if (durationS > 0) {
                    info["fps"] = total / durationS
                }
            }

            // stsd：编码 fourcc
            val stsd = trak.firstOrNull { it.type == "stsd" }
            if (stsd != null) {
                val body = stsd.offset + stsd.headerLen
                info["codec"] = String(data, body + 12, 4, Charsets.ISO_8859_1)
            }

            return info
        }
        return null
    }
}
