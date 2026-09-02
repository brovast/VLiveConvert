package com.vliveconvert.app.core

/** 二进制读写工具：所有大端/小端、数组比较等字节级操作的通用辅助。 */
internal object BinaryUtils {
    /** 比较数组 a 从 aOff 起的子数组是否与 b 完全一致。 */
    fun arrayEquals(a: ByteArray, aOff: Int, b: ByteArray): Boolean {
        if (aOff < 0 || aOff + b.size > a.size) return false
        for (i in b.indices) {
            if (a[aOff + i] != b[i]) return false
        }
        return true
    }

    /** 大端读取无符号 16 位（返回 Int 范围 0..65535）。 */
    fun readU16BE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    /** 大端写入无符号 16 位（v 取低 16 位）。 */
    fun writeU16BE(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 8) and 0xFF).toByte()
        b[off + 1] = (v and 0xFF).toByte()
    }

    /** 大端读取无符号 32 位（返回 Long 范围 0..4294967295）。 */
    fun readU32BE(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
        ((b[off + 1].toLong() and 0xFF) shl 16) or
        ((b[off + 2].toLong() and 0xFF) shl 8) or
        (b[off + 3].toLong() and 0xFF)

    /** 大端写入无符号 32 位。 */
    fun writeU32BE(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v ushr 24) and 0xFF).toByte()
        b[off + 1] = ((v ushr 16) and 0xFF).toByte()
        b[off + 2] = ((v ushr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    /** 大端读取无符号 64 位（返回 Long，可能为负数表示高位被置）。 */
    fun readU64BE(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 56) or
        ((b[off + 1].toLong() and 0xFF) shl 48) or
        ((b[off + 2].toLong() and 0xFF) shl 40) or
        ((b[off + 3].toLong() and 0xFF) shl 32) or
        ((b[off + 4].toLong() and 0xFF) shl 24) or
        ((b[off + 5].toLong() and 0xFF) shl 16) or
        ((b[off + 6].toLong() and 0xFF) shl 8) or
        (b[off + 7].toLong() and 0xFF)

    /** 大端写入无符号 64 位。 */
    fun writeU64BE(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v ushr 56) and 0xFF).toByte()
        b[off + 1] = ((v ushr 48) and 0xFF).toByte()
        b[off + 2] = ((v ushr 40) and 0xFF).toByte()
        b[off + 3] = ((v ushr 32) and 0xFF).toByte()
        b[off + 4] = ((v ushr 24) and 0xFF).toByte()
        b[off + 5] = ((v ushr 16) and 0xFF).toByte()
        b[off + 6] = ((v ushr 8) and 0xFF).toByte()
        b[off + 7] = (v and 0xFF).toByte()
    }

    /** 大端读取有符号 32 位。 */
    fun readI32BE(b: ByteArray, off: Int): Int = readU32BE(b, off).toInt()

    /** 在 data 中从后向前查找 target 子数组，返回起始下标；未找到返回 -1。 */
    fun lastIndexOf(data: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || data.size < target.size) return -1
        val maxStart = data.size - target.size
        for (i in maxStart downTo 0) {
            if (arrayEquals(data, i, target)) return i
        }
        return -1
    }

    /** 在 data 中查找 target 子数组，返回起始下标；未找到返回 -1。 */
    fun indexOf(data: ByteArray, target: ByteArray, from: Int = 0): Int {
        if (target.isEmpty() || data.size - from < target.size) return -1
        val maxStart = data.size - target.size
        var i = from
        while (i <= maxStart) {
            if (arrayEquals(data, i, target)) return i
            i++
        }
        return -1
    }
}
