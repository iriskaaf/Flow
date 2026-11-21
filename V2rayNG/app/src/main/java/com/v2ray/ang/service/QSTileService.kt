package com.v2ray.ang.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils

class QSTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { refreshTile() }
    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    if (request?.active != false) finishRequest()
                }
                AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS -> finishRequest()
            }
        }
    }

    private fun refreshTile() {
        val now = SystemClock.uptimeMillis()
        val pending = request?.takeIf { now < it.expiresAt }
        request = pending
        val active = pending?.active ?: CoreServiceManager.isConnectionActive()
        val title = if (active) (pending?.serverName ?: CoreServiceManager.getRunningServerName()).trim() else ""
        qsTile?.apply {
            icon = Icon.createWithResource(applicationContext, R.drawable.flow_notification)
            label = title.ifEmpty { getString(R.string.app_name) }
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = null
            updateTile()
        }
        handler.removeCallbacks(refresh)
        if (pending != null) handler.postAtTime(refresh, pending.expiresAt)
    }

    private fun finishRequest() {
        request = null
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!listening) {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags(),
            )
            listening = true
        }
        if (request?.active == CoreServiceManager.isConnectionActive()) request = null
        refreshTile()
    }

    override fun onStopListening() {
        stopObserving()
        super.onStopListening()
    }

    override fun onDestroy() {
        stopObserving()
        super.onDestroy()
    }

    private fun stopObserving() {
        handler.removeCallbacks(refresh)
        if (listening) {
            unregisterReceiver(receiver)
            listening = false
        }
    }

    override fun onClick() {
        super.onClick()
        if (qsTile == null || request?.let { SystemClock.uptimeMillis() < it.expiresAt } == true) return
        val enable = !CoreServiceManager.isConnectionActive()
        if (enable && CoreServiceManager.isRunning()) return
        val serverName = if (enable) {
            val profile = MmkvManager.getSelectServer()?.let(MmkvManager::decodeServerConfig)
            if (profile == null || (SettingsManager.isVpnMode() && VpnService.prepare(this) != null)) {
                toast(R.string.app_tile_first_use)
                return
            }
            profile.remarks
        } else ""
        val next = Request(enable, serverName, SystemClock.uptimeMillis() + REQUEST_TIMEOUT_MS)
        request = next
        refreshTile()
        handler.post {
            if (request !== next) return@post
            if (enable) {
                if (!CoreServiceManager.startVServiceFromToggle(this)) finishRequest()
            } else CoreServiceManager.stopVService(this)
        }
    }

    private data class Request(val active: Boolean, val serverName: String, val expiresAt: Long)

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
        // The core and tile share a process, so a SystemUI rebind keeps the requested state.
        var request: Request? = null
    }
}
