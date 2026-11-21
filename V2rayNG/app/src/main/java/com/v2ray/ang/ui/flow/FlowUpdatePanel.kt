package com.v2ray.ang.ui.flow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.FlowUpdate
import com.v2ray.ang.handler.FlowUpdates
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun FlowUpdatePanel() {
    val context = LocalContext.current
    if (context.packageName != FlowUpdates.PACKAGE) return
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<FlowUpdate?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var readyName by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableIntStateOf(0) }

    fun installAllowed() = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstaller() {
        val name = readyName ?: return
        val file = File(File(context.cacheDir, "flow-updates"), name)
        if (!file.isFile) {
            readyName = null
            message = R.string.flow_update_download_error
            return
        }
        try {
            val content = FileProvider.getUriForFile(context, "${context.packageName}.cache", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(content, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            message = 0
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Could not open Flow installer", e)
            message = R.string.flow_update_install_error
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (installAllowed()) openInstaller() else message = R.string.flow_update_permission
    }

    fun requestInstallation() {
        if (installAllowed()) {
            openInstaller()
        } else {
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (e: Exception) {
                message = R.string.flow_update_permission
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            enabled = !checking && progress == null,
            shape = RoundedCornerShape(17.dp),
            onClick = {
                checking = true
                message = 0
                scope.launch {
                    try {
                        update = FlowUpdates.check()
                        message = if (update == null) R.string.flow_update_current else 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Could not check for a Flow release", e)
                        message = R.string.flow_update_check_error
                    } finally {
                        checking = false
                    }
                }
            },
        ) {
            FlowToggleContent(checking, label = "update check label") {
                Text(stringResource(if (it) R.string.flow_update_checking else R.string.flow_update_check))
            }
        }
        update?.let { release ->
            Text(stringResource(R.string.flow_update_available, release.version, Formatter.formatShortFileSize(context, release.size)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            val percent = progress
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.flow_update_downloading, percent), style = MaterialTheme.typography.labelMedium)
            } else if (readyName == null) {
                Button(
                    enabled = !checking,
                    shape = RoundedCornerShape(17.dp),
                    onClick = {
                        progress = 0
                        message = 0
                        scope.launch {
                            try {
                                readyName = FlowUpdates.download(context, release) { progress = it }.name
                                requestInstallation()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                LogUtil.e(AppConfig.TAG, "Could not download or verify the Flow update", e)
                                message = R.string.flow_update_download_error
                            } finally {
                                progress = null
                            }
                        }
                    },
                ) { Text(stringResource(R.string.flow_update_download)) }
            }
        }
        if (readyName != null) {
            Button(onClick = ::requestInstallation, enabled = progress == null, shape = RoundedCornerShape(17.dp)) {
                Text(stringResource(R.string.flow_update_install))
            }
        }
        if (message != 0) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
