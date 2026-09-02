package com.vliveconvert.app.core

import java.util.Calendar

/**
 * 拍摄时间解析：vivo 相机命名（IMG_yyyyMMdd_HHmmss.jpg）等文件名内嵌时间。
 * 用于把文件「修改时间」对齐到照片时间（转换导出与时间修复功能共用）。
 */
internal object PhotoTime {

    // 8 位日期 + 分隔符（_-T 或空格）+ 6 位时间，如 20260831_134432
    private val regex = Regex("""(\d{4})(\d{2})(\d{2})[_\-T ](\d{2})(\d{2})(\d{2})""")

    /** 从文件名解析时间，返回毫秒；未匹配返回 null。 */
    fun parseFromName(name: String): Long? {
        val m = regex.find(name) ?: return null
        return try {
            Calendar.getInstance().apply {
                clear()
                set(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt() - 1,
                    m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(),
                    m.groupValues[5].toInt(),
                    m.groupValues[6].toInt()
                )
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
