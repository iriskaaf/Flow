package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CoreTestService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val probeLock = Mutex()
    private var batch: Job? = null
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message?.key != AppConfig.MSG_MEASURE_CONFIG_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val token = ++generation
        batch?.cancel()
        NotificationHelper.startForeground(
            this, NotificationChannelType.CORE_TEST, getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server),
        )
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, "…")
        batch = scope.launch {
            try {
                // Let native probes from the previous request return before starting the next batch.
                probeLock.withLock {
                    val ids = withContext(Dispatchers.IO) {
                        when {
                            message.serverGuids.isNotEmpty() -> message.serverGuids
                            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
                            else -> MmkvManager.decodeAllServerList()
                        }
                    }
                    RealPingWorkerService(this@CoreTestService).run(ids) { remaining, changed ->
                        if (changed.isNotEmpty()) {
                            MessageUtil.sendMsg2UI(this@CoreTestService, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, changed.joinToString(","))
                        }
                        val status = remaining.toString()
                        NotificationHelper.updateNotification(
                            NotificationChannelType.CORE_TEST, this@CoreTestService,
                            getString(R.string.connection_runing_task_left, status),
                        )
                        MessageUtil.sendMsg2UI(this@CoreTestService, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, status)
                    }
                }
            } finally {
                if (token == generation) {
                    MessageUtil.sendMsg2UI(this@CoreTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, "0")
                    NotificationHelper.stopForeground(this@CoreTestService)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        generation++
        scope.cancel()
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "-1")
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }
}

