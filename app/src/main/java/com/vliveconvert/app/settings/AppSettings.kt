package com.vliveconvert.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.vliveconvert.glass.GlassQuality

/** 画质选项：多一个「自动」，按设备是否低内存动态选择。 */
enum class GlassQualityOption(val label: String) {
    Auto("自动"),
    Off("关闭"),
    Low("省电"),
    Medium("标准"),
    High("极致")
}

enum class SettingsPreset(val label: String) {
    PowerSaver("省电"),
    Balanced("均衡"),
    Performance("性能"),
    Custom("自定义")
}

/** 主界面的照片列表形态。 */
enum class MainViewMode(val label: String) {
    List("列表"),
    Grid("平铺")
}

/**
 * 应用设置（性能 + 外观 + 输出）。
 *
 * 默认值按设备能力计算：并发 2、内存预算为堆上限的 45%。
 */
data class AppSettings(
    val preset: SettingsPreset = SettingsPreset.Balanced,
    val glassQuality: GlassQualityOption = GlassQualityOption.Auto,
    val pauseGlassOnScroll: Boolean = true,
    val animatedBackground: Boolean = false,
    val mainViewMode: MainViewMode = MainViewMode.List,
    val concurrency: Int = 2,
    val memoryLimitMb: Int = defaultMemoryMb(),
    val scanThreads: Int = 4,
    val thumbnailThreads: Int = 4,
    val keepScreenOn: Boolean = true,
    val deleteOriginal: Boolean = false,
    val outputRelPath: String = DEFAULT_OUTPUT_PATH
) {
    fun resolveGlassQuality(isLowRamDevice: Boolean): GlassQuality = when (glassQuality) {
        GlassQualityOption.Auto -> if (isLowRamDevice) GlassQuality.Low else GlassQuality.High
        GlassQualityOption.Off -> GlassQuality.Off
        GlassQualityOption.Low -> GlassQuality.Low
        GlassQualityOption.Medium -> GlassQuality.Medium
        GlassQualityOption.High -> GlassQuality.High
    }

    companion object {
        const val DEFAULT_OUTPUT_PATH = "Pictures/VLiveConvert"
        private const val MB = 1024L * 1024L

        fun maxHeapMb(): Int =
            (Runtime.getRuntime().maxMemory() / MB).toInt().coerceAtLeast(128)

        fun memoryUpperBoundMb(): Int =
            (maxHeapMb() * 0.70f).toInt().coerceAtLeast(128)

        fun defaultMemoryMb(): Int =
            (maxHeapMb() * 0.45f).toInt().coerceIn(128, memoryUpperBoundMb())

        fun maxConcurrency(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

        fun cpuCores(): Int = Runtime.getRuntime().availableProcessors()
    }
}

/**
 * 性能预设：只改性能相关字段，保留输出目录/删除原图等用户选择。
 */
fun AppSettings.withPreset(preset: SettingsPreset): AppSettings {
    val upper = AppSettings.memoryUpperBoundMb()
    val cores = AppSettings.cpuCores()
    return when (preset) {
        SettingsPreset.PowerSaver -> copy(
            preset = preset,
            glassQuality = GlassQualityOption.Low,
            pauseGlassOnScroll = true,
            animatedBackground = false,
            concurrency = 1,
            memoryLimitMb = (AppSettings.maxHeapMb() * 0.30f).toInt()
                .coerceIn(128, upper),
            scanThreads = 2,
            thumbnailThreads = 2
        )

        SettingsPreset.Balanced -> copy(
            preset = preset,
            glassQuality = GlassQualityOption.Auto,
            pauseGlassOnScroll = true,
            animatedBackground = false,
            concurrency = 2,
            memoryLimitMb = AppSettings.defaultMemoryMb(),
            scanThreads = 4,
            thumbnailThreads = 4
        )

        SettingsPreset.Performance -> copy(
            preset = preset,
            glassQuality = GlassQualityOption.High,
            pauseGlassOnScroll = true,
            animatedBackground = true,
            concurrency = cores.coerceIn(2, 6),
            memoryLimitMb = (AppSettings.maxHeapMb() * 0.60f).toInt()
                .coerceIn(128, upper),
            scanThreads = cores.coerceIn(2, 8),
            thumbnailThreads = cores.coerceIn(2, 8)
        )

        SettingsPreset.Custom -> copy(preset = SettingsPreset.Custom)
    }
}

/** SharedPreferences 持久化。键名与旧版本保持兼容。 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("vliveconvert", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val upper = AppSettings.memoryUpperBoundMb()
        val defaults = AppSettings()
        return AppSettings(
            preset = runCatching {
                SettingsPreset.valueOf(prefs.getString("settings_preset", null) ?: return@runCatching SettingsPreset.Balanced)
            }.getOrDefault(SettingsPreset.Balanced),
            glassQuality = runCatching {
                GlassQualityOption.valueOf(
                    prefs.getString("glass_quality", null) ?: return@runCatching GlassQualityOption.Auto
                )
            }.getOrDefault(GlassQualityOption.Auto),
            pauseGlassOnScroll = prefs.getBoolean(
                "glass_pause_scroll", defaults.pauseGlassOnScroll
            ),
            animatedBackground = prefs.getBoolean(
                "glass_animated_bg", defaults.animatedBackground
            ),
            mainViewMode = runCatching {
                MainViewMode.valueOf(
                    prefs.getString("main_view_mode", null) ?: return@runCatching MainViewMode.List
                )
            }.getOrDefault(MainViewMode.List),
            concurrency = prefs.getInt("convert_concurrency", defaults.concurrency)
                .coerceIn(1, AppSettings.maxConcurrency()),
            memoryLimitMb = prefs.getInt("convert_memory_mb", defaults.memoryLimitMb)
                .coerceIn(128, upper),
            scanThreads = prefs.getInt("scan_threads", defaults.scanThreads)
                .coerceIn(1, 8),
            thumbnailThreads = prefs.getInt("thumb_threads", defaults.thumbnailThreads)
                .coerceIn(1, 8),
            keepScreenOn = prefs.getBoolean("keep_screen_on", defaults.keepScreenOn),
            deleteOriginal = prefs.getBoolean("delete_original", defaults.deleteOriginal),
            outputRelPath = prefs.getString("output_rel_path", null)
                ?.takeIf { it.isNotBlank() }
                ?: AppSettings.DEFAULT_OUTPUT_PATH
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("settings_preset", settings.preset.name)
            .putString("glass_quality", settings.glassQuality.name)
            .putBoolean("glass_pause_scroll", settings.pauseGlassOnScroll)
            .putBoolean("glass_animated_bg", settings.animatedBackground)
            .putString("main_view_mode", settings.mainViewMode.name)
            .putInt("convert_concurrency", settings.concurrency)
            .putInt("convert_memory_mb", settings.memoryLimitMb)
            .putInt("scan_threads", settings.scanThreads)
            .putInt("thumb_threads", settings.thumbnailThreads)
            .putBoolean("keep_screen_on", settings.keepScreenOn)
            .putBoolean("delete_original", settings.deleteOriginal)
            .putString("output_rel_path", settings.outputRelPath)
            .apply()
    }
}
