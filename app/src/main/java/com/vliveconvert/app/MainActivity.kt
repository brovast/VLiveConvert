package com.vliveconvert.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vliveconvert.app.convert.Converter
import com.vliveconvert.app.core.PhotoTime
import com.vliveconvert.app.picker.AlbumInfo
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.picker.MediaRepo
import com.vliveconvert.app.picker.PickerScanner
import com.vliveconvert.app.picker.PickerScreen
import com.vliveconvert.app.picker.SingleLiveScanner
import com.vliveconvert.app.ui.ConvertItem
import com.vliveconvert.app.ui.FixTimeScreen
import com.vliveconvert.app.ui.MainScreen
import com.vliveconvert.app.ui.PermissionScreen
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    // ---------- 状态 ----------
    private val items = mutableStateListOf<ConvertItem>()
    private var statusText by mutableStateOf("")
    private var isConverting by mutableStateOf(false)
    private var progress by mutableFloatStateOf(0f)
    private var progressDetail by mutableStateOf("")

    // 内置选择器
    private lateinit var mediaRepo: MediaRepo
    private lateinit var scanner: PickerScanner
    private var showPicker by mutableStateOf(false)
    private var pickerAlbums by mutableStateOf<List<AlbumInfo>>(emptyList())

    // 权限
    private var showSettingsDialog by mutableStateOf(false)
    // 所有文件访问权限引导弹窗（删除原图 / 修复时间功能需要时提示）
    private var showAllFilesDialog by mutableStateOf(false)
    // 权限授予后要执行的动作（默认进入内置选择器）
    private var pendingPermissionAction: (() -> Unit)? = null
    // 所有文件访问权限授予后要执行的动作（如进入修复时间界面）
    private var pendingAfterAllFiles: (() -> Unit)? = null

    // 转换后删除原图（持久化开关）
    private var deleteOriginal by mutableStateOf(false)
    /** 本批次成功后待删除的原图 URI（jpg + 伴生 mp4） */
    private val pendingDeleteUris = java.util.Collections.synchronizedList(mutableListOf<Uri>())
    private var deleteBaseStatus = ""
    private var pendingDeleteCount = 0
    /** 本次待写入回收站的 URI（确认成功后转持久化恢复记录） */
    private var lastTrashUris: List<String> = emptyList()
    /** 回收站中的原图记录数（未授权所有文件访问的删除路径），应用内可恢复（30 天内） */
    private var pendingRestoreCount by mutableIntStateOf(0)

    // 转换暂存目录（应用缓存，导出后清理）
    private lateinit var tempOutDir: File

    // 修复文件时间（把单文件实况的「修改时间」按文件名时间修正；相册式选择，同双文件选择器）
    private var showFixTime by mutableStateOf(false)
    private lateinit var singleLiveScanner: SingleLiveScanner
    private var fixAlbums by mutableStateOf<List<AlbumInfo>>(emptyList())
    private var fixStatus by mutableStateOf("")
    private var isFixing by mutableStateOf(false)
    private var fixProgress by mutableFloatStateOf(0f)
    private var fixSelectionReset by mutableIntStateOf(0)

    // 自定义输出目录（MediaStore 相对路径，默认 Pictures/VLiveConvert）
    private var outputRelPath by mutableStateOf("Pictures/VLiveConvert")
    private var showOutputPathDialog by mutableStateOf(false)
    private var outputPathInput by mutableStateOf("")
    // 输出文件批量移动到 DCIM/Camera
    private var isMovingOutputs by mutableStateOf(false)

    // 动态照片 = 图片 + 伴生视频，必须同时申请图片与视频读取权限
    // （双文件格式需要直接读取同目录 .mp4，缺视频权限会报 EACCES）
    private fun requiredReadPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )

    // 媒体访问能力（不含 ACCESS_MEDIA_LOCATION）：任一媒体读取权限授予即可进入选择器
    private fun hasReadPermission(): Boolean =
        requiredReadPermissions()
            .filter { it != Manifest.permission.ACCESS_MEDIA_LOCATION }
            .any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            val videoGranted = result.entries
                .filter { it.key == Manifest.permission.READ_MEDIA_VIDEO }
                .all { it.value } ||
                result.entries
                    .filter { it.key == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
                    .all { it.value }
            statusText = if (videoGranted) "已获得读取照片和视频权限"
                         else "已授权，但视频权限缺失：无法找到双文件实况的伴生视频"
            val action = pendingPermissionAction
            pendingPermissionAction = null
            (action ?: { openBuiltInPicker() })()
        } else {
            statusText = "未授予权限，可点击「授予权限」重新申请"
        }
    }

    // 跳转系统设置后返回时刷新状态
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (hasReadPermission()) statusText = "已获得读取照片和视频权限" }

    // 删除原图：系统回收站工具（createTrashRequest）结果回调——整批仅一次请求
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 记录本批被回收的原图，供应用内「恢复原图」撤销（vivo 相册不显示此类项目）
            if (lastTrashUris.isNotEmpty()) {
                addTrashedRecords(lastTrashUris)
                lastTrashUris = emptyList()
                refreshRestoreCount()
            }
            statusText = "$deleteBaseStatus；原图已移入系统回收站（$pendingDeleteCount 项，" +
                "以隐藏形式保留 30 天，可在本应用「恢复原图」）"
        } else {
            statusText = "$deleteBaseStatus；已取消删除原图"
        }
        pendingDeleteUris.clear()
    }

    // 恢复原图：系统确认弹窗（createTrashRequest(uris, false)）结果回调
    private val restoreRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val n = clearTrashedRecords()
            statusText = "已恢复 $n 项原图到原位置"
        } else {
            statusText = "已取消恢复原图"
        }
    }

    // 所有文件访问权限设置页返回：刷新提示，并继续授权前挂起的动作
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val action = pendingAfterAllFiles
        pendingAfterAllFiles = null
        if (Environment.isExternalStorageManager()) {
            statusText = "已授予所有文件访问权限"
            action?.invoke()
        } else {
            statusText = "未授予所有文件访问权限：修复文件时间与静默删除原图暂不可用"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tempOutDir = File(cacheDir, "output").apply { mkdirs() }
        // 启动时清理上次残留的暂存产物
        lifecycleScope.launch(Dispatchers.IO) {
            try { tempOutDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
        }

        mediaRepo = MediaRepo(contentResolver)
        scanner = PickerScanner(mediaRepo)
        singleLiveScanner = SingleLiveScanner(mediaRepo)

        // 恢复删除原图开关 + 刷新可恢复记录数
        deleteOriginal = getSharedPreferences("vliveconvert", MODE_PRIVATE)
            .getBoolean("delete_original", false)
        refreshRestoreCount()
        // 恢复自定义输出目录
        outputRelPath = getSharedPreferences("vliveconvert", MODE_PRIVATE)
            .getString("output_rel_path", "Pictures/VLiveConvert") ?: "Pictures/VLiveConvert"

        setContent {
            VLiveConvertTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (!hasReadPermission()) {
                        PermissionScreen(
                            onRequest = { requestReadPermissions(null) },
                            statusText = statusText
                        )
                    } else if (showPicker) {
                        PickerScreen(
                            albums = pickerAlbums,
                            scanner = scanner,
                            scannerScope = lifecycleScope,
                            onBack = { showPicker = false },
                            onConfirm = { picked ->
                                showPicker = false
                                addPickedItems(picked)
                            }
                        )
                    } else if (showFixTime) {
                        FixTimeScreen(
                            albums = fixAlbums,
                            scanner = singleLiveScanner,
                            scannerScope = lifecycleScope,
                            isFixing = isFixing,
                            progress = fixProgress,
                            statusText = fixStatus,
                            selectionReset = fixSelectionReset,
                            onBack = { showFixTime = false },
                            onFix = { selected -> startFixTimes(selected) }
                        )
                    } else {
                        MainScreen(
                            items = items.toList(),
                            statusText = statusText,
                            isConverting = isConverting,
                            progress = progress,
                            progressDetail = progressDetail,
                            pendingRestoreCount = pendingRestoreCount,
                            onRestoreOriginals = { restoreTrashedOriginals() },
                            outputRelPath = outputRelPath,
                            isMovingOutputs = isMovingOutputs,
                            onEditOutputPath = {
                                outputPathInput = outputRelPath
                                showOutputPathDialog = true
                            },
                            onMoveToCamera = { moveOutputsToCamera() },
                            onOpenFixTime = { openFixTime() },
                            deleteOriginal = deleteOriginal,
                            onToggleDeleteOriginal = { on ->
                                // 开启且未授予所有文件访问权限时，提示授权以去掉系统确认框
                                if (on && !Environment.isExternalStorageManager()) {
                                    showAllFilesDialog = true
                                }
                                deleteOriginal = on
                                getSharedPreferences("vliveconvert", MODE_PRIVATE)
                                    .edit().putBoolean("delete_original", on).apply()
                            },
                            onAddMore = { openBuiltInPicker() },
                            onStartConvert = { startConvert() },
                            onClearAll = {
                                items.clear()
                                statusText = "已清空"
                            },
                            onRemove = { ci ->
                                items.removeAll { it.item.key == ci.item.key }
                            }
                        )
                    }
                }

                // 所有文件访问权限引导（删除原图 / 修复时间功能需要时）
                if (showAllFilesDialog) {
                    AlertDialog(
                        onDismissRequest = { showAllFilesDialog = false },
                        title = { Text("需要「所有文件访问权限」") },
                        text = {
                            Text(
                                "以下功能依赖「所有文件访问权限」：\n\n" +
                                "• 修复文件时间：修改已导出文件的「修改时间」属性\n\n" +
                                "• 转换后删除原图：删除不再弹系统确认框，被删文件会进入 vivo 相册的" +
                                "「第三方删除拦截」，可在相册中恢复；未授权时删除走系统回收站" +
                                "（30 天内可在本应用恢复）\n\n" +
                                "是否前往授权？"
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showAllFilesDialog = false
                                openAllFilesAccessSettings()
                            }) { Text("去授权") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showAllFilesDialog = false }) { Text("暂不") }
                        }
                    )
                }

                // 输出目录修改弹窗
                if (showOutputPathDialog) {
                    AlertDialog(
                        onDismissRequest = { showOutputPathDialog = false },
                        title = { Text("输出目录") },
                        text = {
                            Column {
                                Text(
                                    "相对主存储的路径，导出的单文件实况会保存到这里；留空恢复默认。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = outputPathInput,
                                    onValueChange = { outputPathInput = it },
                                    singleLine = true,
                                    label = { Text("例如 Pictures/VLiveConvert") }
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val raw = outputPathInput.trim()
                                if (raw.isEmpty()) {
                                    outputRelPath = "Pictures/VLiveConvert"
                                    getSharedPreferences("vliveconvert", MODE_PRIVATE)
                                        .edit().putString("output_rel_path", outputRelPath).apply()
                                    showOutputPathDialog = false
                                    statusText = "输出目录已恢复默认：$outputRelPath"
                                } else {
                                    val s = sanitizeRelPath(raw)
                                    if (s == null) {
                                        statusText = "路径无效：不能包含 \\ : * ? \" < > | 或 ..（可留空恢复默认）"
                                    } else {
                                        outputRelPath = s
                                        getSharedPreferences("vliveconvert", MODE_PRIVATE)
                                            .edit().putString("output_rel_path", s).apply()
                                        showOutputPathDialog = false
                                        statusText = "输出目录已设为：$s"
                                    }
                                }
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showOutputPathDialog = false }) { Text("取消") }
                        }
                    )
                }

                // 跳转设置对话框（用户选了「不再询问」）
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("需要手动授予照片和视频权限") },
                        text = {
                            Text(
                                "您之前选择了「不再询问」，系统不再弹出权限对话框。\n\n" +
                                "请前往应用详情页 → 权限 → 照片和视频，手动授予访问权限后返回本应用。\n\n" +
                                "注意：必须同时授予「照片」和「视频」权限，否则双文件实况将无法找到附带的伴生视频。"
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showSettingsDialog = false
                                openAppDetailSettings()
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showSettingsDialog = false }) { Text("取消") }
                        }
                    )
                }
            }
        }
    }

    // ---------- 权限 ----------

    private fun requestReadPermissions(after: (() -> Unit)?) {
        pendingPermissionAction = after
        requestPermissionLauncher.launch(requiredReadPermissions())
    }

    private fun openAppDetailSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            settingsLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    /** 跳转「所有文件访问权限」设置页（本应用入口；失败时回退到总开关页/应用详情） */
    private fun openAllFilesAccessSettings() {
        try {
            allFilesAccessLauncher.launch(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) {
            try {
                allFilesAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                openAppDetailSettings()
            }
        }
    }

    // ---------- 内置选择器 ----------

    /** 打开内置选择器：只展示双文件实况照片 */
    private fun openBuiltInPicker() {
        if (isConverting) return
        if (!hasReadPermission()) {
            requestReadPermissions { openBuiltInPicker() }
            return
        }
        scanner.newSession() // 每次进入选择器开启新会话（相册扫过即不再扫）
        statusText = "正在读取相册…"
        lifecycleScope.launch(Dispatchers.IO) {
            val albums = mediaRepo.queryAlbums()
            withContext(Dispatchers.Main) {
                pickerAlbums = albums
                showPicker = true
                statusText = if (albums.isEmpty()) "未找到相册" else ""
            }
        }
    }

    /** 选择器确认：把选中的双文件实况加入转换列表（按 id 去重） */
    private fun addPickedItems(picked: List<MediaItem>) {
        if (picked.isEmpty()) return
        var added = 0
        for (item in picked) {
            if (items.any { it.item.id == item.id }) continue
            items.add(ConvertItem(item = item))
            added++
        }
        statusText = if (added > 0) "已添加 $added 张，共 ${items.size} 张待转换"
                     else "所选照片已在列表中"
    }

    // ---------- 转换 ----------

    private fun startConvert() {
        if (isConverting) return
        val targets = items.filter { !it.done && !it.failed }
        if (targets.isEmpty()) {
            statusText = "没有待转换的照片"
            return
        }

        isConverting = true
        progress = 0f
        progressDetail = "已处理 0/${targets.size}"

        lifecycleScope.launch(Dispatchers.IO) {
            // 4 路并发转换（Semaphore 限流），单文件异常隔离不中断
            val sem = Semaphore(4)
            val total = targets.size
            val done = AtomicInteger(0)
            val ok = AtomicInteger(0)
            val fail = AtomicInteger(0)

            val jobs = targets.map { ci ->
                launch {
                    sem.withPermit {
                        withContext(Dispatchers.Main) {
                            replaceItem(ci, ci.copy(status = "转换中…"))
                        }
                        var staged: String? = null
                        try {
                            staged = Converter.convertToVivoSingle(
                                path = ci.item.path,
                                outDir = tempOutDir.absolutePath,
                                log = { level, msg, _ ->
                                    if (level == "warning") {
                                        // 日志回调在 IO 线程触发，状态写入需切回 Main
                                        lifecycleScope.launch(Dispatchers.Main) { statusText = msg }
                                    }
                                }
                            )
                            // 拍摄时间优先；缺失回退文件名解析，再回退修改时间
                            val ts = when {
                                ci.item.dateTaken > 0 -> ci.item.dateTaken
                                else -> PhotoTime.parseFromName(ci.item.name)
                                    ?: (if (ci.item.dateModified > 0) ci.item.dateModified * 1000L
                                        else System.currentTimeMillis())
                            }
                            exportToMediaStore(staged, ts)
                            ok.incrementAndGet()
                            // 开关开启：收集本项原图（jpg + 伴生 mp4），批次结束统一删除
                            if (deleteOriginal) collectOriginalUris(ci)
                            withContext(Dispatchers.Main) {
                                replaceItem(ci, ci.copy(
                                    status = "完成：已导出到相册 $outputRelPath",
                                    done = true
                                ))
                            }
                        } catch (e: Exception) {
                            fail.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                replaceItem(ci, ci.copy(
                                    status = "失败：${e.message ?: "未知错误"}",
                                    failed = true
                                ))
                            }
                        } finally {
                            // 清理本地暂存产物（已导出 / 失败均清理）
                            staged?.let { p -> try { File(p).delete() } catch (_: Exception) {} }
                        }
                        val d = done.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            progress = d.toFloat() / total
                            progressDetail = "已处理 $d/$total"
                        }
                    }
                }
            }
            jobs.joinAll()
            withContext(Dispatchers.Main) {
                isConverting = false
                progress = 0f
                progressDetail = ""
                statusText = "转换完成：成功 ${ok.get()} 个，失败 ${fail.get()} 个" +
                    "（输出目录：$outputRelPath）"
                // 删除原图开关：批次完成后一次性请求把成功项的原图移入回收站
                if (deleteOriginal) requestDeleteOriginals(statusText)
                // 清理暂存目录
                lifecycleScope.launch(Dispatchers.IO) {
                    try { tempOutDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
                }
            }
        }
    }

    /** 按源文件 key 替换列表项（Main 线程调用） */
    private fun replaceItem(old: ConvertItem, new: ConvertItem) {
        val idx = items.indexOfFirst { it.item.key == old.item.key }
        if (idx >= 0) items[idx] = new
    }

    // ---------- 修复文件时间 ----------

    /** 打开修复时间界面（需「所有文件访问权限」：未授权时引导授权，授权后自动进入） */
    private fun openFixTime() {
        if (isFixing) return
        if (!Environment.isExternalStorageManager()) {
            pendingAfterAllFiles = { openFixTime() }
            showAllFilesDialog = true
            return
        }
        showFixTime = true
        fixStatus = "正在读取相册…"
        lifecycleScope.launch(Dispatchers.IO) {
            val albums = mediaRepo.queryAlbums()
            withContext(Dispatchers.Main) {
                fixAlbums = albums
                fixStatus = if (albums.isEmpty()) "未找到相册" else ""
            }
        }
    }

    /** 批量修复所选单文件实况的「修改时间」：文件名时间 → 拍摄时间，两者都无则跳过 */
    private fun startFixTimes(targets: List<MediaItem>) {
        if (targets.isEmpty() || isFixing) return
        isFixing = true
        fixProgress = 0f
        fixStatus = ""
        lifecycleScope.launch(Dispatchers.IO) {
            var ok = 0
            var skip = 0
            for ((idx, item) in targets.withIndex()) {
                val time = PhotoTime.parseFromName(item.name)
                    ?: (if (item.dateTaken > 0) item.dateTaken else 0L)
                if (time <= 0L) {
                    skip++
                } else {
                    try {
                        // 物理文件 mtime（应用导出的文件可直改）+ 媒体库列双写
                        val mtimeOk = try {
                            File(item.path).setLastModified(time)
                        } catch (_: Exception) {
                            false
                        }
                        contentResolver.update(
                            item.uri,
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DATE_MODIFIED, time / 1000)
                                put(MediaStore.MediaColumns.DATE_TAKEN, time)
                            },
                            null, null
                        )
                        ok++
                        if (!mtimeOk) {
                            withContext(Dispatchers.Main) {
                                fixStatus = "部分文件 mtime 修改未生效（已更新媒体库时间）：${item.name}"
                            }
                        }
                    } catch (_: Exception) {
                        skip++
                    }
                }
                withContext(Dispatchers.Main) {
                    fixProgress = (idx + 1).toFloat() / targets.size
                }
            }
            withContext(Dispatchers.Main) {
                isFixing = false
                fixSelectionReset++
                fixStatus = "修复完成：成功 $ok 项，跳过 $skip 项" +
                    (if (skip > 0) "（文件名中无时间信息或不可写）" else "")
            }
        }
    }

    // ---------- 导出 ----------

    /**
     * 导出到系统相册（Pictures/VLiveConvert）。
     * MediaStore 标准写入（IS_PENDING，写完才出现在相册）；
     * 输出为单个 .jpg（vivo 单文件实况）。
     */
    private fun exportToMediaStore(srcPath: String, timestamp: Long) {
        val src = File(srcPath)
        if (!src.exists() || src.length() == 0L) {
            throw IOException("转换产物缺失或为空")
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, outputRelPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
            put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert 返回 null")
        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                src.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("MediaStore 输出流不可用")

            // 部分设备会在写入完成后用真实写入时间覆盖拍摄时间，固化一次
            try {
                val ts = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                    put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                }
                contentResolver.update(uri, ts, null, null)
            } catch (_: Exception) {}

            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null
            )

            // 物理文件的 mtime 也固化为拍摄时间：系统显示的「修改时间」来自文件 mtime，
            // 只改 MediaStore 列的话会在媒体扫描时被文件 mtime 覆盖回写入时刻
            try {
                val dataPath = contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                if (dataPath != null) {
                    File(dataPath).setLastModified(timestamp)
                }
            } catch (_: Exception) {}

            // 最后再固化一次媒体库列（DATE_MODIFIED / DATE_TAKEN 均为拍摄时间）
            try {
                contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                        put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                    },
                    null, null
                )
            } catch (_: Exception) {}
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            throw IOException("写入相册失败：${e.message}")
        }
    }

    // ---------- 转换后删除原图 ----------

    /**
     * 收集待删除原图 URI（IO 线程调用）：
     * jpg URI 直接用选择器所得媒体 URI；伴生 mp4 按 DATA 路径回查视频集合。
     */
    private fun collectOriginalUris(ci: ConvertItem) {
        try { pendingDeleteUris.add(ci.item.uri) } catch (_: Exception) {}
        val stem = ci.item.path.substringBeforeLast('.')
        val mp4Path = "$stem.mp4"
        val mp4 = File(mp4Path)
        if (mp4.exists() && mp4.length() > 8L) {
            resolveVideoUriByPath(mp4Path)?.let { pendingDeleteUris.add(it) }
        }
    }

    /** 按 DATA 绝对路径在视频媒体库查 URI */
    private fun resolveVideoUriByPath(path: String): Uri? {
        return try {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val id = contentResolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            id?.let { android.content.ContentUris.withAppendedId(collection, it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 批次结束后一次性请求删除原图（含伴生视频）：
     * - 已授予「所有文件访问权限」→ 直接删除，无需确认
     * - 否则 createTrashRequest 整批一次系统确认弹窗，确认后移入回收站
     */
    private fun requestDeleteOriginals(baseStatus: String) {
        val all = synchronized(pendingDeleteUris) { pendingDeleteUris.distinct().toList() }
        pendingDeleteUris.clear()
        if (all.isEmpty()) return
        // 过滤已失效条目，避免请求抛异常
        val valid = all.filter { uri ->
            try {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                    ?.use { it.moveToFirst() } == true
            } catch (_: Exception) {
                false
            }
        }
        if (valid.isEmpty()) {
            statusText = "$baseStatus；原图删除失败（无法访问原文件）"
            return
        }
        deleteBaseStatus = baseStatus
        if (Environment.isExternalStorageManager()) {
            var deleted = 0
            for (uri in valid) {
                try { if (contentResolver.delete(uri, null, null) > 0) deleted++ } catch (_: Exception) {}
            }
            statusText = "$baseStatus；已直接删除 $deleted 个原文件" +
                "（vivo 相册「第三方删除拦截」中可查看/恢复）"
            return
        }
        try {
            val sender = MediaStore.createTrashRequest(contentResolver, valid, true).intentSender
            pendingDeleteCount = valid.size
            lastTrashUris = valid.map { it.toString() }
            deleteRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: Exception) {
            statusText = "$baseStatus；原图移入回收站失败（${e.message}）"
            lastTrashUris = emptyList()
        }
    }

    // ---------- 原图恢复（回收站路径的删除，30 天内可在应用内撤销） ----------

    private val RESTORE_WINDOW_MS = 30L * 24 * 3600 * 1000

    private data class TrashedRecord(val uri: String, val time: Long)

    private fun trashedPrefs() = getSharedPreferences("vliveconvert", MODE_PRIVATE)

    /** 读取回收站记录（过滤超过 30 天窗口的过期条目） */
    private fun loadTrashedRecords(): List<TrashedRecord> {
        return try {
            val arr = JSONArray(trashedPrefs().getString("trashed_records", "[]"))
            val cutoff = System.currentTimeMillis() - RESTORE_WINDOW_MS
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val u = o.optString("uri")
                val t = o.optLong("time")
                if (u.isNotEmpty() && t >= cutoff) TrashedRecord(u, t) else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveTrashedRecords(list: List<TrashedRecord>) {
        try {
            val arr = JSONArray()
            list.takeLast(1000).forEach { r ->
                arr.put(JSONObject().put("uri", r.uri).put("time", r.time))
            }
            trashedPrefs().edit().putString("trashed_records", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun addTrashedRecords(uris: List<String>) {
        val current = loadTrashedRecords()
        val existing = current.map { it.uri }.toHashSet()
        val now = System.currentTimeMillis()
        saveTrashedRecords(current + uris.filter { it !in existing }.map { TrashedRecord(it, now) })
    }

    private fun clearTrashedRecords(): Int {
        val n = loadTrashedRecords().size
        saveTrashedRecords(emptyList())
        pendingRestoreCount = 0
        return n
    }

    private fun refreshRestoreCount() {
        pendingRestoreCount = loadTrashedRecords().size
    }

    /**
     * 应用内恢复原图：把回收站中的记录（含伴生视频）通过系统确认弹窗恢复到原位置。
     * 仅覆盖「未授权所有文件访问」的回收站删除路径；已授权的直接删除由
     * vivo 相册「第三方删除拦截」负责恢复。
     */
    private fun restoreTrashedOriginals() {
        val uris = loadTrashedRecords()
            .mapNotNull { r -> try { Uri.parse(r.uri) } catch (_: Exception) { null } }
            .filter { uri ->
                try {
                    contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                        ?.use { it.moveToFirst() } == true
                } catch (_: Exception) {
                    false
                }
            }
        if (uris.isEmpty()) {
            clearTrashedRecords()
            statusText = "没有可恢复的原图（记录已过期或文件已被系统清理）"
            return
        }
        try {
            val sender = MediaStore.createTrashRequest(contentResolver, uris, false).intentSender
            restoreRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: Exception) {
            statusText = "恢复原图失败（${e.message}）"
        }
    }

    // ---------- 输出目录与移动 ----------

    /** 清洗用户输入的相对路径；非法返回 null */
    private fun sanitizeRelPath(input: String): String? {
        val s = input.trim().replace('\\', '/').trim('/')
        if (s.isEmpty() || s.contains("..")) return null
        if (s.split('/').any { it.isEmpty() || it == "." }) return null
        if (Regex("""[:*?"<>|]""").containsMatchIn(s)) return null
        return s
    }

    /** 查询指定相对路径下的全部图片（按加入时间降序；兼容带/不带尾斜杠两种存储形态） */
    private fun queryImagesIn(relPath: String): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        try {
            contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?,?)",
                arrayOf("$relPath/", relPath),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iModified = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val path = c.getString(iPath) ?: continue
                    result.add(MediaItem(
                        id = c.getLong(iId),
                        path = path,
                        name = c.getString(iName) ?: path.substringAfterLast('/'),
                        bucketId = 0L,
                        dateTaken = c.getLong(iTaken),
                        dateModified = c.getLong(iModified),
                        size = c.getLong(iSize)
                    ))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /** DCIM/Camera 内的唯一名（与现有文件重名时追加 (n)） */
    private fun uniqueCameraName(displayName: String): String {
        val taken = HashSet<String>()
        try {
            contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf("DCIM/Camera/"),
                null
            )?.use { c ->
                val i = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) taken.add(c.getString(i) ?: "")
            }
        } catch (_: Exception) {}
        if (displayName !in taken) return displayName
        val stem = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val cand = if (ext.isEmpty()) "$stem($i)" else "$stem($i).$ext"
            if (cand !in taken) return cand
            i++
        }
    }

    /**
     * 把单个输出文件移动到 DCIM/Camera（拍摄/修改时间不变）：
     * ① 应用是文件所有者，直接更新 RELATIVE_PATH（MediaStore 原生移动，不复制数据）；
     * ② 失败回退：在 DCIM/Camera 插入新条目并流式复制内容，再删除原条目。
     */
    private fun moveOneToCamera(item: MediaItem): Boolean {
        val newName = uniqueCameraName(item.name)
        try {
            val rows = contentResolver.update(
                item.uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                },
                null, null
            )
            if (rows > 0) return true
        } catch (_: Exception) {}
        var newUri: Uri? = null
        try {
            newUri = contentResolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                    if (item.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateModified)
                }
            ) ?: return false
            contentResolver.openOutputStream(newUri, "w")?.use { out ->
                contentResolver.openInputStream(item.uri)?.use { input -> input.copyTo(out) }
            } ?: throw IOException("输出流不可用")
            contentResolver.delete(item.uri, null, null)
            return true
        } catch (e: Exception) {
            newUri?.let { u -> try { contentResolver.delete(u, null, null) } catch (_: Exception) {} }
            return false
        }
    }

    /** 把输出目录中的全部已转换文件移动到 DCIM/Camera */
    private fun moveOutputsToCamera() {
        if (isMovingOutputs || isConverting) return
        if (outputRelPath.equals("DCIM/Camera", ignoreCase = true)) {
            statusText = "输出目录已是 DCIM/Camera，无需移动"
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isMovingOutputs = true }
            val items = queryImagesIn(outputRelPath)
            if (items.isEmpty()) {
                withContext(Dispatchers.Main) {
                    isMovingOutputs = false
                    statusText = "输出目录（$outputRelPath）中没有可移动的文件"
                }
                return@launch
            }
            var ok = 0
            var fail = 0
            for ((idx, item) in items.withIndex()) {
                if (moveOneToCamera(item)) ok++ else fail++
                withContext(Dispatchers.Main) {
                    statusText = "正在移动到 DCIM/Camera…${idx + 1}/${items.size}"
                }
            }
            withContext(Dispatchers.Main) {
                isMovingOutputs = false
                statusText = "移动完成：$ok 个文件已移到 DCIM/Camera" +
                    (if (fail > 0) "，失败 $fail 个" else "")
            }
        }
    }
}
