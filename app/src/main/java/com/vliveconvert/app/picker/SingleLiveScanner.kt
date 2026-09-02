package com.vliveconvert.app.picker

import androidx.compose.runtime.mutableStateListOf
import com.vliveconvert.app.core.PhotoTime
import com.vliveconvert.app.core.XmpTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 单文件实况照片扫描引擎：多线程识别已内嵌视频的实况（vivo 单文件 / Google Motion Photo /
 * 小米等，判定 = XMP 含 MotionPhoto/MicroVideo 标记）。
 * 供「修复文件时间」界面使用，会话化扫描策略与双文件选择器（PickerScanner）一致：
 * - 仅在进入界面时开启新会话
 * - 每个相册一次会话内只扫一次；未扫完切走即中断，切回重扫
 * - 结果仅存内存
 */
class SingleLiveScanner(private val repo: MediaRepo) {

    class ScanState {
        val results = mutableStateListOf<MediaItem>()
        val known = ConcurrentHashMap<String, MediaItem>()
        val pending = ConcurrentLinkedQueue<MediaItem>()
        val scanned = ConcurrentHashMap.newKeySet<String>()
        val doneCount = AtomicInteger(0)
        @Volatile var total = 0
        @Volatile var running = false
        @Volatile var completed = false
        var job: Job? = null

        fun resetProgress() {
            results.clear()
            known.clear()
            pending.clear()
            scanned.clear()
            doneCount.set(0)
            total = 0
        }
    }

    private val states = ConcurrentHashMap<Long, ScanState>()
    @Volatile private var activeBucket: Long? = null

    fun stateOf(bucketId: Long): ScanState = states.getOrPut(bucketId) { ScanState() }

    /** 开启新会话：中断并清空全部相册的扫描状态 */
    fun newSession() {
        states.values.forEach { it.job?.cancel() }
        states.clear()
        activeBucket = null
    }

    fun enter(bucketId: Long, scope: CoroutineScope) {
        activeBucket = bucketId
        val st = stateOf(bucketId)
        if (st.completed) return
        st.job?.cancel()
        st.job = scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { st.resetProgress() }
            if (!currentCoroutineContext().isActive) return@launch
            if (diffRefresh(st, bucketId)) {
                st.running = true
                val workers = (1..SCAN_THREADS).map {
                    launch { scanWorker(st) }
                }
                workers.forEach { it.join() }
                if (st.pending.isEmpty()) {
                    st.running = false
                    st.completed = true
                }
            }
        }
    }

    fun leave(bucketId: Long) {
        states[bucketId]?.job?.cancel()
        if (activeBucket == bucketId) activeBucket = null
    }

    private suspend fun scanWorker(st: ScanState) {
        while (currentCoroutineContext().isActive) {
            val item = st.pending.poll() ?: break
            if (!st.known.containsKey(item.key)) continue
            if (shouldInclude(item)) {
                withContext(Dispatchers.Main) {
                    if (st.known.containsKey(item.key) &&
                        st.results.none { it.id == item.id }
                    ) {
                        st.results.add(item)
                    }
                }
            }
            st.scanned.add(item.key)
            st.doneCount.set(st.scanned.size)
        }
    }

    /**
     * 是否列入修复清单（按成本排序的判定链）：
     * 1) JPG 扩展名；
     * 2) 能确定目标时间（文件名时间 → 拍摄时间；两者皆无则无法修复，排除）；
     * 3) 当前「修改时间」与目标时间不一致（差值 > 2 秒；已一致的无需修复，排除）；
     * 4) XMP 含内嵌动态照片标记（单文件实况）。
     * 已一致/无目标时间的文件在读文件内容之前即被排除，扫描开销最小。
     */
    private fun shouldInclude(item: MediaItem): Boolean {
        val path = item.path
        if (!path.endsWith(".jpg", ignoreCase = true) &&
            !path.endsWith(".jpeg", ignoreCase = true)
        ) return false
        val target = PhotoTime.parseFromName(item.name)
            ?: (if (item.dateTaken > 0) item.dateTaken else return false)
        val current = try {
            File(path).lastModified()
        } catch (_: Exception) {
            return false
        }
        if (Math.abs(current - target) <= MTIME_TOLERANCE_MS) return false
        return try {
            XmpTemplate.parseMotionXmp(XmpTemplate.sniffXmp(path, 512 * 1024)).isMotion
        } catch (_: Exception) {
            false
        }
    }

    private fun diffRefresh(st: ScanState, bucketId: Long): Boolean {
        val current = repo.queryAlbumImages(bucketId)
        for (item in current) {
            st.known[item.key] = item
            st.pending.add(item)
        }
        st.total = current.size
        return st.pending.isNotEmpty()
    }

    companion object {
        private const val SCAN_THREADS = 4

        /** 修改时间与目标时间差值容忍度（2 秒，文件系统秒级精度内视为一致） */
        private const val MTIME_TOLERANCE_MS = 2_000L
    }
}
