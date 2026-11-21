package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.flow.Choice
import com.v2ray.ang.ui.flow.FlowColors
import com.v2ray.ang.ui.flow.FlowProfiles
import com.v2ray.ang.ui.flow.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = intent.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
        setContent {
            MaterialExpressiveTheme(colorScheme = FlowColors) {
                var start by rememberSaveable { mutableStateOf(config?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH) ?: false) }
                var id by rememberSaveable {
                    mutableStateOf(config?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID) ?: AppConfig.TASKER_DEFAULT_GUID)
                }
                val servers by produceState(FlowProfiles()) { value = withContext(Dispatchers.IO) { FlowProfiles.read() } }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                        Text(stringResource(R.string.flow_tasker_title), style = MaterialTheme.typography.headlineMedium)
                        ToggleRow(R.string.flow_tasker_start, start, { start = it })
                        LazyColumn(Modifier.weight(1f)) {
                            item {
                                Choice(stringResource(R.string.flow_tasker_default), id == AppConfig.TASKER_DEFAULT_GUID) {
                                    id = AppConfig.TASKER_DEFAULT_GUID
                                }
                            }
                            items(servers.servers, key = { it.id }) { server ->
                                Choice(server.name, id == server.id) { id = server.id }
                            }
                        }
                        Button(
                            onClick = {
                                val target = servers.servers.firstOrNull { it.id == id }?.name ?: getString(R.string.flow_tasker_default)
                                val result = Intent().putExtra(AppConfig.TASKER_EXTRA_BUNDLE, Bundle().apply {
                                    putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, start)
                                    putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, id)
                                }).putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB,
                                    getString(if (start) R.string.flow_tasker_start else R.string.flow_tasker_stop) + " · " + target)
                                setResult(RESULT_OK, result)
                                finish()
                            },
                            shapes = ButtonDefaults.shapes(RoundedCornerShape(24.dp), RoundedCornerShape(13.dp)),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.flow_save)) }
                    }
                }
            }
        }
    }
}

