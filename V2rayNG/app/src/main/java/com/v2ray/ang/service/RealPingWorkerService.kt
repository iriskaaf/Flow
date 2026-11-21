package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class RealPingWorkerService(private val context: Context) {
    suspend fun run(guids: List<String>, progress: (Int, List<String>) -> Unit) = coroutineScope {
        val ids = withContext(Dispatchers.IO) {
            val reachable = ArrayList<String>()
            val untested = ArrayList<String>()
            val failed = ArrayList<String>()
            for (id in guids.distinct()) {
                val previousDelay = MmkvManager.decodeServerAffiliationInfo(id)?.testDelayMillis ?: 0L
                when {
                    previousDelay > 0L -> reachable.add(id)
                    previousDelay == 0L -> untested.add(id)
                    else -> failed.add(id)
                }
            }
            reachable.addAll(untested)
            reachable.addAll(failed)
            reachable
        }
        val url = SettingsManager.getDelayTestUrl()
        val concurrency = minOf(ids.size, SettingsManager.getRealPingConcurrency(), if (ids.size > 45) 25 else 10)
        val cursor = AtomicInteger()
        val completed = AtomicInteger()
        val changes = ConcurrentLinkedQueue<String>()

        fun publish() {
            val changed = buildList {
                while (true) add(changes.poll() ?: break)
            }
            progress(ids.size - completed.get(), changed)
        }

        val notifier = launch {
            delay(550)
            while (true) {
                publish()
                delay(550)
            }
        }
        val probes = List(concurrency) {
            launch(Dispatchers.IO) {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val id = ids.getOrNull(cursor.getAndIncrement()) ?: break
                    val delayMillis = probe(id, url)
                    currentCoroutineContext().ensureActive()
                    if (MmkvManager.serverExists(id)) {
                        MmkvManager.encodeServerTestDelayMillis(id, delayMillis)
                        changes.add(id)
                    }
                    completed.incrementAndGet()
                }
            }
        }
        try {
            probes.joinAll()
            publish()
        } finally {
            notifier.cancel()
        }
    }

    private fun probe(guid: String, url: String): Long = runCatching {
        val live = CoreServiceManager.measureRunningDelay(guid, url)
        if (live != null && live >= 0L) return@runCatching live.coerceAtLeast(1L)
        val config = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!config.status) return@runCatching -1L
        val delay = CoreNativeManager.measureOutboundDelay(config.content, url)
        if (delay >= 0L) delay.coerceAtLeast(1L) else -1L
    }.getOrDefault(-1L)
}

