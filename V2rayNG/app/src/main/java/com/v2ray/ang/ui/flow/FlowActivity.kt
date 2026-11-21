package com.v2ray.ang.ui.flow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.SubscriptionDeviceError
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.tencent.mmkv.MMKV
import com.v2ray.ang.enums.Language
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.FlowPreferences
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.serverDescription
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.ZipUtil
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.viewmodel.UserAssetViewModel
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FlowActivity : BaseActivity() {
    companion object {
        const val ACTION_SCAN = "com.v2ray.ang.action.FLOW_SCAN"
        const val EXTRA_IMPORT = "com.v2ray.ang.extra.FLOW_IMPORT"
        internal const val HOME_PING = "home-ping"
    }

    private val serviceState: MainViewModel by viewModels()
    private var profiles by mutableStateOf(FlowProfiles())
    private var running by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var working by mutableStateOf(false)
    private var pinging by mutableStateOf(false)
    internal var pingTarget by mutableStateOf<String?>(null)
        private set
    private var systemColors by mutableStateOf(false)
    private var autoConnect by mutableStateOf(false)
    private var detectCountry by mutableStateOf(false)
    private var subscriptionHwid by mutableStateOf(true)
    private var welcomeCompleted by mutableStateOf(false)
    private var dynamicIsland by mutableStateOf(true)
    private var pendingSwitch: String? = null
    private var snapshotJob: Job? = null
    private var connectionTimeout: Job? = null
    private var delayUpdate: Job? = null
    private val pendingDelays = linkedSetOf<String>()

    private val qrScanner = registerForActivityResult(ScanCustomCode()) { result ->
        if (result is QRResult.QRSuccess) result.content.rawValue?.let(::importText)
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) requestNotificationsOrStart() else finishTransition()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startConnection()
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask {
            val ok = restoreBackup { target ->
                contentResolver.openInputStream(uri)?.use { source ->
                    target.outputStream().use { source.copyTo(it) }
                } != null
            }
            getString(if (ok) R.string.toast_success else R.string.toast_failure)
        }
    }
    private val backupPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runTask {
            val ok = createBackup { source ->
                contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } != null
            }
            getString(if (ok) R.string.toast_success else R.string.toast_failure)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        welcomeCompleted = MmkvManager.decodeSettingsBool(FlowPreferences.WELCOME_COMPLETED)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        serviceState.startListenBroadcast(legacyList = false)
        serviceState.isRunning.observe(this) { active ->
            running = active
            val next = pendingSwitch
            if (!active && next != null) {
                pendingSwitch = null
                lifecycleScope.launch {
                    delay(550L)
                    MmkvManager.setSelectServer(next)
                    reload()
                    startConnection()
                }
            } else {
                finishTransition()
                reload()
            }
        }
        serviceState.pinging.observe(this) { pinging = it }
        serviceState.pingResultIds.observe(this) { ids ->
            pendingDelays.addAll(ids)
            updateDelays()
        }
        serviceState.initAssets(assets)
        SubscriptionUpdater.sync()

        setContent {
            FlowScreen(profiles, running, pending, working, systemColors, autoConnect, detectCountry,
                subscriptionHwid, welcomeCompleted, pinging, dynamicIsland, this)
        }
        if (savedInstanceState == null) handleFlowIntent(intent)

        if (savedInstanceState == null && welcomeCompleted &&
            MmkvManager.decodeSettingsBool(FlowPreferences.CONNECT_ON_LAUNCH)
        ) {
            lifecycleScope.launch {
                delay(500L)
                if (!running && MmkvManager.getSelectServer()?.isNotBlank() == true) toggleVpn()
            }
        }
    }

    internal fun completeWelcome() {
        MmkvManager.encodeSettings(FlowPreferences.WELCOME_COMPLETED, true)
        welcomeCompleted = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFlowIntent(intent)
    }

    private fun handleFlowIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_IMPORT)?.takeIf { it.isNotBlank() }?.let(::importText)
        if (intent.action == ACTION_SCAN) scanQr()
    }

    override fun onResume() {
        super.onResume()
        refreshPreferences()
        SettingsChangeManager.consumeSetupGroupTab()
        if (SettingsChangeManager.consumeRestartService() && running) {
            profiles.selected?.let { chooseServer(it, reconnect = true) }
        }
        reload()
    }

    private fun refreshPreferences() {
        systemColors = MmkvManager.decodeSettingsBool(FlowPreferences.SYSTEM_COLORS)
        autoConnect = MmkvManager.decodeSettingsBool(FlowPreferences.CONNECT_ON_LAUNCH)
        detectCountry = MmkvManager.decodeSettingsBool(FlowPreferences.LOOK_UP_EXIT_COUNTRY)
        subscriptionHwid = MmkvManager.decodeSettingsBool(FlowPreferences.SUBSCRIPTION_HWID, true)
        dynamicIsland = MmkvManager.decodeSettingsBool(FlowPreferences.DYNAMIC_ISLAND, true)
    }

    private fun reload(wait: Long = 0L) {
        snapshotJob?.cancel()
        snapshotJob = lifecycleScope.launch {
            delay(wait)
            profiles = withContext(Dispatchers.IO) { FlowProfiles.read() }
        }
    }

    private fun updateDelays() {
        if (delayUpdate?.isActive == true) return
        delayUpdate = lifecycleScope.launch {
            while (pendingDelays.isNotEmpty()) {
                val ids = pendingDelays.toSet()
                pendingDelays.clear()
                val snapshot = profiles
                val updated = withContext(Dispatchers.IO) { snapshot.refreshDelays(ids) }
                if (snapshot === profiles) profiles = updated else pendingDelays.addAll(ids)
            }
        }
    }

    internal fun toggleVpn() {
        if (pending) return
        if (running) {
            startTransition()
            CoreServiceManager.stopVService(this)
            return
        }
        if (profiles.active == null) {
            toast(R.string.flow_no_selection)
            return
        }
        pending = true
        val permission = if (SettingsManager.isVpnMode()) VpnService.prepare(this) else null
        if (permission == null) requestNotificationsOrStart() else vpnPermission.launch(permission)
    }

    private fun requestNotificationsOrStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startConnection()
        }
    }

    private fun startConnection() {
        startTransition()
        if (!CoreServiceManager.startVServiceFromToggle(this)) finishTransition()
    }

    private fun startTransition() {
        pending = true
        connectionTimeout?.cancel()
        connectionTimeout = lifecycleScope.launch {
            delay(12_000)
            finishTransition()
        }
    }

    private fun finishTransition() {
        pending = false
        pendingSwitch = null
        connectionTimeout?.cancel()
        connectionTimeout = null
    }

    internal fun chooseServer(id: String, reconnect: Boolean = false) {
        if (pending || (profiles.selected == id && !reconnect)) return
        val previous = profiles.active?.takeIf { it.id != id && it.favorite }
        val order = previous?.let { FlowPreferences.moveFavoriteToEnd(it.preferenceKey, profiles.favoriteOrder) }
            ?: profiles.favoriteOrder
        profiles = profiles.copy(selected = id, favoriteOrder = order)
        if (running) {
            snapshotJob?.cancel()
            startTransition()
            pendingSwitch = id
            CoreServiceManager.stopVService(this)
        } else {
            MmkvManager.setSelectServer(id)
            if (snapshotJob?.isActive == true) reload()
        }
    }

    internal fun favorite(server: FlowServer) {
        if (server.sectionLabel != null) return
        val order = FlowPreferences.toggleFavorite(server.preferenceKey, profiles.favoriteOrder)
        profiles = profiles.updateFavorite(server.preferenceKey, order)
        if (snapshotJob?.isActive == true) reload()
    }

    internal fun changeAutomaticDividers(enabled: Boolean) {
        val active = profiles.active
        val overrides = FlowPreferences.dividerOverrides()
        if (enabled && active != null && FlowPreferences.dividerLabel(active.profile, true, overrides[active.preferenceKey]) != null) {
            if (running || pending) {
                toast(R.string.toast_action_not_allowed)
                return
            }
            val next = profiles.servers.firstOrNull {
                FlowPreferences.dividerLabel(it.profile, true, overrides[it.preferenceKey]) == null
            }
            MmkvManager.setSelectServer(next?.id.orEmpty())
        }
        MmkvManager.encodeSettings(FlowPreferences.AUTOMATIC_DIVIDERS, enabled)
        reload()
    }

    internal fun toggleDivider(server: FlowServer) {
        val divider = server.sectionLabel == null
        if (divider && profiles.selected == server.id) {
            if (running || pending) {
                toast(R.string.toast_action_not_allowed)
                return
            }
            MmkvManager.setSelectServer(profiles.servers.firstOrNull { it.id != server.id }?.id.orEmpty())
        }
        FlowPreferences.setDivider(server.preferenceKey, divider)
        reload()
    }

    internal fun saveCountry(server: FlowServer, code: String?) {
        FlowPreferences.setCountry(server.preferenceKey, code)
        if (running && profiles.selected == server.id) toast(R.string.flow_set_country_reconnect)
        reload()
    }

    internal fun remove(server: FlowServer) {
        if (pending || (running && profiles.selected == server.id)) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { MmkvManager.removeServer(server.id) }
            reload()
        }
    }

    internal fun saveProfile(server: FlowServer, profile: ProfileItem, raw: String?) = runTask {
        if (raw != null) MmkvManager.encodeServerRaw(server.id, raw)
        MmkvManager.encodeServerConfig(server.id, if (raw == null) profile else profile.copy(
            serverDescription = JsonUtil.parseString(raw)?.serverDescription(),
        ))
        FlowPreferences.migrateProfilePreferences(mapOf(server.preferenceKey to FlowPreferences.profileKey(server.id, profile)))
        getString(R.string.toast_success)
    }

    internal fun testServers(ids: List<String>, target: String? = null) {
        if (ids.isEmpty()) return
        pingTarget = target
        MessageUtil.sendMsg2TestService(this, TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            serverGuids = ids,
        ))
    }

    internal fun testHomeServers() = testServers(profiles.homePingIds, HOME_PING)

    internal fun moveSubscription(id: String, offset: Int) {
        val order = MmkvManager.decodeSubsList()
        val ids = profiles.groups.filter { it.subscription != null }.map(FlowGroup::id).toSet()
        val visible = order.filter(ids::contains)
        val current = visible.indexOf(id)
        if (current < 0) return
        val neighbor = visible.getOrNull(current + offset) ?: return
        val sourceIndex = order.indexOf(id)
        val destinationIndex = order.indexOf(neighbor)
        order[sourceIndex] = neighbor
        order[destinationIndex] = id
        MmkvManager.encodeSubsList(order)
        val positions = order.withIndex().associate { (index, key) -> key to index }
        profiles = profiles.copy(groups = profiles.groups.sortedBy { positions[it.id] ?: Int.MAX_VALUE })
        reload()
    }

    internal fun importText(value: String) = importProfiles { value }
    internal fun importClipboard() = importText(Utils.getClipboard(this).orEmpty())
    internal fun exportBackup() = backupPicker.launch("Flow-backup.zip")
    internal fun importBackup() = filePicker.launch(arrayOf("application/zip", "application/octet-stream"))

    internal fun webDavBackup(config: WebDavConfig, upload: Boolean) = runTask {
        MmkvManager.encodeWebDavConfig(config)
        WebDavManager.init(config)
        val ok = if (upload) createBackup { WebDavManager.uploadFile(it, AppConfig.WEBDAV_BACKUP_FILE_NAME) }
        else restoreBackup { WebDavManager.downloadFile(AppConfig.WEBDAV_BACKUP_FILE_NAME, it) }
        getString(if (ok) R.string.toast_success else R.string.toast_failure)
    }

    private suspend fun createBackup(transfer: suspend (File) -> Boolean): Boolean {
        val directory = File(cacheDir, "flow-backup-" + Utils.getUuid())
        val data = File(directory, "data")
        val archive = File(directory, "backup.zip")
        return try {
            data.mkdirs() && MMKV.backupAllToDirectory(data.path) > 0 &&
                ZipUtil.zipFromFolder(data.path, archive.path) && transfer(archive)
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun restoreBackup(transfer: suspend (File) -> Boolean): Boolean {
        val directory = File(cacheDir, "flow-restore-" + Utils.getUuid())
        val data = File(directory, "data")
        val archive = File(directory, "backup.zip")
        return try {
            val restored = directory.mkdirs() && transfer(archive) && ZipUtil.unzipToFolder(archive, data.path) &&
                MMKV.restoreAllFromDirectory(data.path) > 0
            if (restored) {
                SettingsManager.initApp(this)
                withContext(Dispatchers.Main) {
                    refreshPreferences()
                    restartForSettings()
                }
            }
            restored
        } finally {
            directory.deleteRecursively()
        }
    }

    internal fun refreshGeodata() = runTask {
        val result = UserAssetViewModel().downloadGeoFiles(
            File(Utils.userAssetPath(this)), SettingsManager.getHttpPort(),
            SettingsManager.getSocksUsername(), SettingsManager.getSocksPassword(),
        )
        getString(if (result.failureCount == 0) R.string.toast_success else R.string.toast_failure)
    }
    internal fun scanQr() {
        qrScanner.launch(ScannerConfig.build {
            setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
            setShowCloseButton(true)
            setShowTorchToggle(true)
        })
    }

    private fun importProfiles(read: () -> String?) = runTask {
        val previous = MmkvManager.decodeSubscriptions().map { it.guid }.toSet()
        val (servers, subscriptions) =
            AngConfigManager.importBatchConfig(read(), "", true, refreshSubscriptions = false)
        val refreshed = if (subscriptions > 0) MmkvManager.decodeSubscriptions()
            .filter { it.guid !in previous }
            .fold(SubscriptionUpdateResult()) { total, item -> total + AngConfigManager.updateConfigViaSub(item) }
        else null
        when {
            refreshed?.deviceError != null -> subscriptionMessage(refreshed)
            servers > 0 -> getString(R.string.flow_imported, servers)
            refreshed != null -> subscriptionMessage(refreshed)
            else -> getString(R.string.toast_failure)
        }
    }

    internal fun saveSubscription(group: FlowGroup?, item: SubscriptionItem) = runTask {
        val id = group?.id ?: Utils.getUuid()
        MmkvManager.encodeSubscription(id, item)
        SubscriptionUpdater.sync()
        if (group == null || group.subscription?.subscription?.url != item.url) {
            subscriptionMessage(MmkvManager.decodeSubscriptions().firstOrNull { it.guid == id }
                ?.let(AngConfigManager::updateConfigViaSub))
        } else getString(R.string.toast_success)
    }

    internal fun removeSubscription(group: FlowGroup) {
        if (running && profiles.active?.profile?.subscriptionId == group.id) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        runTask {
            MmkvManager.removeSubscription(group.id)
            SubscriptionUpdater.sync()
            getString(R.string.toast_success)
        }
    }

    internal fun refresh(group: FlowGroup) {
        val subscription = group.subscription ?: return
        runTask {
            val result = AngConfigManager.updateConfigViaSub(subscription)
            subscriptionMessage(result)
        }
    }

    private fun subscriptionMessage(result: SubscriptionUpdateResult?): String = getString(when {
        result?.deviceError == SubscriptionDeviceError.LIMIT_REACHED -> R.string.flow_hwid_limit
        result?.deviceError == SubscriptionDeviceError.REQUIRED -> R.string.flow_hwid_required
        (result?.successCount ?: 0) > 0 -> R.string.flow_subscription_updated
        else -> R.string.flow_subscription_failed
    })

    private fun runTask(action: suspend () -> String) {
        if (working) return
        working = true
        lifecycleScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    runCatching { action() }.getOrElse { getString(R.string.toast_failure) }
                }
                toast(message)
            } finally {
                working = false
                reload()
            }
        }
    }

    internal fun changeTheme(enabled: Boolean) {
        MmkvManager.encodeSettings(FlowPreferences.SYSTEM_COLORS, enabled)
        systemColors = enabled
    }

    internal fun changeAutoConnect(enabled: Boolean) {
        MmkvManager.encodeSettings(FlowPreferences.CONNECT_ON_LAUNCH, enabled)
        autoConnect = enabled
    }

    internal fun changeCountryDetection(enabled: Boolean) {
        MmkvManager.encodeSettings(FlowPreferences.LOOK_UP_EXIT_COUNTRY, enabled)
        detectCountry = enabled
    }

    internal fun changeSubscriptionHwid(enabled: Boolean) {
        MmkvManager.encodeSettings(FlowPreferences.SUBSCRIPTION_HWID, enabled)
        subscriptionHwid = enabled
    }

    internal fun changeDynamicIsland(enabled: Boolean) {
        MmkvManager.encodeSettings(FlowPreferences.DYNAMIC_ISLAND, enabled)
        dynamicIsland = enabled
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_FLOW_ISLAND_CHANGED, "")
    }

    internal fun changeLanguage(language: Language) {
        MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, language.code)
        recreate()
    }

    internal fun restartForSettings() {
        if (running) MessageUtil.sendMsg2Service(this, AppConfig.MSG_STATE_RESTART, "")
    }

    internal fun openAndroidVpnSettings() = startActivity(Intent(Settings.ACTION_VPN_SETTINGS))

    internal fun openNotificationSettings() {
        val intent = Intent(if (Build.VERSION.SDK_INT >= 36) {
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
        } else if (Build.VERSION.SDK_INT >= 26) {
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (intent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            intent.data = android.net.Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
        }
    }
}
