package com.vliveconvert.app.convert

import com.vliveconvert.app.ui.ConvertItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * 转换调度器：并发上限 + 内存预算 + OOM 自动降级。
 *
 * 旧实现固定 4 路并发，每张照片在字节级重组时会产生 2~3 倍源文件大小的瞬时副本；
 * 大图批次（300+ 张）很容易触顶应用堆上限，`OutOfMemoryError` 又会直接杀死进程。
 *
 * 这里做三件事：
 * 1. 按 JPG + 伴生 MP4 大小估算单张峰值，向共享内存预算申请配额；
 * 2. 并发数与用户设置一致，预算不足时自动排队，而不是把设备内存打满；
 * 3. 捕获 OOM，把失败项收集起来在批次末尾单线程重试一次。
 */
class ConversionScheduler(
    private val concurrency: Int,
    private val memoryBudgetMb: Int,
    private val outDir: String
) {

    sealed interface Outcome {
        data class Ok(val stagedPath: String) : Outcome
        data class Failed(
            val message: String,
            val oom: Boolean = false
        ) : Outcome
    }

    private val budgetBytes: Long =
        (memoryBudgetMb.toLong() * 1024L * 1024L)
            .coerceAtLeast(16L * 1024L * 1024L)

    private val budgetLock = Mutex()
    private var usedBytes: Long = 0L
    private val jobCounter = AtomicInteger(0)

    /**
     * @param onStart 真正开始转换时回调（主线程）
     * @param onResult 单张结束回调（主线程）
     * @param onProgress (已完成, 总数)，主线程
     * @param onWarning 转换器警告（可能来自 IO 线程）
     */
    suspend fun run(
        targets: List<ConvertItem>,
        onStart: suspend (ConvertItem) -> Unit,
        onResult: suspend (ConvertItem, Outcome) -> Unit,
        onProgress: suspend (done: Int, total: Int) -> Unit,
        onWarning: (String) -> Unit = {}
    ) {
        val total = targets.size
        if (total == 0) return

        val done = AtomicInteger(0)
        val oomRetry = Collections.synchronizedList(mutableListOf<ConvertItem>())
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))

        coroutineScope {
            targets.map { ci ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val outcome = convertOne(
                            ci = ci,
                            allowRetryOnOom = true,
                            onStart = onStart,
                            onWarning = onWarning
                        )
                        if (outcome is Outcome.Failed && outcome.oom) {
                            oomRetry.add(ci)
                        } else {
                            withContext(Dispatchers.Main) { onResult(ci, outcome) }
                            cleanupJobDir(outcome)
                            val d = done.incrementAndGet()
                            withContext(Dispatchers.Main) { onProgress(d, total) }
                        }
                    }
                }
            }.joinAll()
        }

        // OOM 降级：批次末尾单线程重试，此时其它任务已释放内存
        val retries = synchronized(oomRetry) { oomRetry.toList() }
        if (retries.isNotEmpty()) {
            for (ci in retries) {
                val outcome = convertOne(
                    ci = ci,
                    allowRetryOnOom = false,
                    onStart = onStart,
                    onWarning = onWarning
                )
                withContext(Dispatchers.Main) { onResult(ci, outcome) }
                cleanupJobDir(outcome)
                val d = done.incrementAndGet()
                withContext(Dispatchers.Main) { onProgress(d, total) }
            }
        }
    }

    /** 导出完成后删除本次转换的独立暫存子目录。 */
    private fun cleanupJobDir(outcome: Outcome) {
        if (outcome is Outcome.Ok) {
            try {
                val parent = File(outcome.stagedPath).parentFile
                if (parent != null && parent.name.startsWith("job_")) {
                    parent.deleteRecursively()
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun convertOne(
        ci: ConvertItem,
        allowRetryOnOom: Boolean,
        onStart: suspend (ConvertItem) -> Unit,
        onWarning: (String) -> Unit
    ): Outcome {
        val estimate = estimatePeakBytes(ci)
        return withBudget(estimate) {
            withContext(Dispatchers.Main) { onStart(ci) }
            // 每张使用独立子目录，避免不同相册同名文件（IMG_xxx.jpg）并发互相覆盖
            val jobDir = File(
                outDir,
                "job_${System.nanoTime()}_${jobCounter.incrementAndGet()}"
            ).apply { mkdirs() }
            try {
                val staged = Converter.convertToVivoSingle(
                    path = ci.item.path,
                    outDir = jobDir.absolutePath,
                    log = { level, msg, _ ->
                        if (level == "warning") onWarning(msg)
                    }
                )
                Outcome.Ok(staged)
            } catch (e: OutOfMemoryError) {
                jobDir.deleteRecursively()
                if (allowRetryOnOom) {
                    Outcome.Failed("内存不足，将在批次末尾单线程重试", oom = true)
                } else {
                    Outcome.Failed("内存不足：请降低并发数/内存上限后重试")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                jobDir.deleteRecursively()
                throw e
            } catch (e: Exception) {
                jobDir.deleteRecursively()
                Outcome.Failed(e.message ?: "未知错误")
            }
        }
    }

    private fun estimatePeakBytes(ci: ConvertItem): Long {
        val jpg = try {
            File(ci.item.path).length()
        } catch (_: Exception) {
            0L
        }
        val mp4 = try {
            File(ci.item.path.substringBeforeLast('.') + ".mp4").length()
        } catch (_: Exception) {
            0L
        }
        // 流式写出后单张峰值约为：JPG(原数组 + primary) + MP4(原数组 + 插入 lpex 的结果)
        return ((jpg + mp4) * 2L + 16L * 1024L * 1024L)
            .coerceAtLeast(24L * 1024L * 1024L)
    }

    private suspend fun <T> withBudget(charge: Long, block: suspend () -> T): T {
        val want = charge.coerceIn(8L * 1024L * 1024L, budgetBytes)
        while (true) {
            val granted = budgetLock.withLock {
                if (usedBytes == 0L || usedBytes + want <= budgetBytes) {
                    usedBytes += want
                    true
                } else {
                    false
                }
            }
            if (granted) break
            delay(40)
        }
        try {
            return block()
        } finally {
            budgetLock.withLock {
                usedBytes = (usedBytes - want).coerceAtLeast(0L)
            }
        }
    }
}
