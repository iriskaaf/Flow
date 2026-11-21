package com.v2ray.ang.ui.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.FlowPreferences
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.SubscriptionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class FlowDialog {
    ADD, SUBSCRIPTION, REMOVE_SUBSCRIPTION, PROFILE, EDIT_PROFILE, COUNTRY, REMOVE, REORDER,
}

@Composable
internal fun FlowDialogs(
    dialog: FlowDialog?,
    details: FlowServer?,
    subscription: FlowGroup?,
    groups: List<FlowGroup>,
    actions: FlowActivity,
    show: (FlowDialog?) -> Unit,
) {
    val dismiss = { show(null) }
    when (dialog) {
        null -> Unit
        FlowDialog.ADD -> FlowSheet(stringResource(R.string.flow_add), dismiss) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                ImportSquare(R.drawable.flow_symbol_content_paste, R.string.flow_clipboard) {
                    dismiss()
                    actions.importClipboard()
                }
                ImportSquare(R.drawable.flow_symbol_qr_code_scanner, R.string.flow_qr) {
                    dismiss()
                    actions.scanQr()
                }
            }
        }
        FlowDialog.SUBSCRIPTION -> {
            val current = subscription?.subscription?.subscription
            var name by rememberSaveable(subscription?.id) { mutableStateOf(subscription?.title.orEmpty()) }
            var url by rememberSaveable(subscription?.id) { mutableStateOf(current?.url.orEmpty()) }
            var enabled by rememberSaveable(subscription?.id) { mutableStateOf(current?.enabled ?: true) }
            var auto by rememberSaveable(subscription?.id) { mutableStateOf(current?.autoUpdate ?: false) }
            val valid = url.isNotBlank() && Utils.isValidSubUrl(url.trim()) &&
                android.net.Uri.parse(url.trim()).host?.isNotBlank() == true
            EditorSheet(if (current == null) R.string.flow_add_subscription else R.string.flow_subscription_options,
                R.string.flow_save, dismiss, valid, {
                dismiss()
                val item = current?.copy() ?: SubscriptionItem()
                item.automaticTitle = name.isBlank() || (current?.automaticTitle == true && subscription?.title == name.trim())
                item.remarks = SubscriptionTitle.display(name.trim(), url.trim())
                item.url = url.trim()
                item.enabled = enabled
                item.autoUpdate = auto
                actions.saveSubscription(subscription, item)
            }) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.flow_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.flow_url)) },
                    isError = url.isNotBlank() && !valid, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    supportingText = {
                        if (url.isNotBlank() && !valid) Text(stringResource(R.string.flow_invalid_url))
                    })
                ToggleRow(R.string.flow_subscription_enabled, enabled, { enabled = it })
                ToggleRow(R.string.flow_subscription_auto, auto, { auto = it })
                if (current != null) {
                    if (groups.count { it.subscription != null } > 1) {
                        SheetAction(R.drawable.flow_symbol_swap_vert, R.string.flow_reorder_subscriptions) { show(FlowDialog.REORDER) }
                    }
                    SheetAction(R.drawable.ic_delete_24dp, R.string.flow_remove_subscription) { show(FlowDialog.REMOVE_SUBSCRIPTION) }
                }
            }
        }
        FlowDialog.REMOVE_SUBSCRIPTION -> if (subscription != null) {
            InputDialog(R.string.flow_remove_subscription, R.string.flow_remove, dismiss, true, {
                dismiss()
                actions.removeSubscription(subscription)
            }) { Text(subscription.title.orEmpty()) }
        }
        FlowDialog.PROFILE -> if (details != null) {
            FlowSheet(details.name, dismiss) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (details.sectionLabel == null) {
                        SheetAction(if (details.favorite) R.drawable.flow_star_filled else R.drawable.flow_star_outline,
                            if (details.favorite) R.string.flow_favorite_remove else R.string.flow_favorite_add) {
                            actions.favorite(details)
                            dismiss()
                        }
                        SheetAction(R.drawable.flow_android, R.string.flow_country) { show(FlowDialog.COUNTRY) }
                        SheetAction(R.drawable.flow_speed, R.string.flow_latency) {
                            dismiss()
                            actions.testServers(listOf(details.id))
                        }
                    }
                    SheetAction(R.drawable.flow_symbol_dns,
                        if (details.sectionLabel == null) R.string.flow_make_divider else R.string.flow_make_server) {
                        actions.toggleDivider(details)
                        dismiss()
                    }
                    SheetAction(R.drawable.ic_edit_24dp, R.string.flow_edit) { show(FlowDialog.EDIT_PROFILE) }
                    SheetAction(R.drawable.ic_delete_24dp, R.string.flow_remove) { show(FlowDialog.REMOVE) }
                }
            }
        }
        FlowDialog.EDIT_PROFILE -> if (details != null) ProfileEditor(details, actions, dismiss)
        FlowDialog.COUNTRY -> if (details != null) {
            var code by rememberSaveable(details.preferenceKey) {
                mutableStateOf(FlowPreferences.countryOverride(details.preferenceKey).orEmpty())
            }
            val normalized = FlowPreferences.normalizeCountry(code)
            val valid = code.isBlank() || normalized != null
            InputDialog(R.string.flow_country, R.string.flow_save, dismiss, valid, {
                actions.saveCountry(details, normalized)
                dismiss()
            }) {
                OutlinedTextField(code, { code = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.flow_country_code)) }, isError = !valid,
                    supportingText = {
                        Text(stringResource(if (valid) R.string.flow_country_unknown else R.string.flow_country_invalid))
                    })
                if (normalized != null) Flag(normalized)
            }
        }
        FlowDialog.REMOVE -> if (details != null) {
            InputDialog(R.string.flow_remove_confirm, R.string.flow_remove, dismiss, true, {
                dismiss()
                actions.remove(details)
            }) { Text(details.name) }
        }
        FlowDialog.REORDER -> FlowAlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.flow_reorder_subscriptions)) },
            text = {
                val ordered = groups.filter { it.subscription != null }
                val colors = MaterialTheme.colorScheme
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ordered, key = { _, group -> group.id }) { index, group ->
                        val title = group.title.orEmpty()
                        Row(
                            Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(17.dp))
                                .background(colors.surfaceContainer).padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, Modifier.weight(1f).padding(start = 5.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ActionSquare(R.drawable.flow_arrow, R.string.flow_move_up, 44.dp, colors.surfaceContainerHigh, colors.onSurface,
                                { actions.moveSubscription(group.id, -1) }, index > 0, rotation = -90f,
                                description = stringResource(R.string.flow_move_up, title))
                            ActionSquare(R.drawable.flow_arrow, R.string.flow_move_down, 44.dp, colors.surfaceContainerHigh, colors.onSurface,
                                { actions.moveSubscription(group.id, 1) }, index < ordered.lastIndex, rotation = 90f,
                                description = stringResource(R.string.flow_move_down, title))
                        }
                    }
                }
            },
            confirmButton = { DialogAction(R.string.flow_close, dismiss) },
        )
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val radius = flowChoiceRadius(selected, 10.dp, 24.dp)
    val fill by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer,
        motion.fastEffectsSpec(), label = "choice fill")
    val mark by animateFloatAsState(if (selected) 1f else 0f, motion.fastSpatialSpec(), label = "choice mark")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).flowPress(interaction).clip(RoundedCornerShape(radius))
            .background(fill).selectable(selected, interaction, ripple(), role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface)
        Box(Modifier.size(23.dp).graphicsLayer {
            alpha = mark.coerceIn(0f, 1f)
            scaleX = .6f + mark * .4f
            scaleY = scaleX
        }, contentAlignment = Alignment.Center) {
            FlowIcon(R.drawable.flow_status_on, 18.dp, colors.onSecondaryContainer)
        }
    }
}

@Composable
internal fun MenuAction(label: Int, click: () -> Unit) {
    TextButton(
        onClick = click,
        shapes = ButtonDefaults.shapes(RoundedCornerShape(20.dp), RoundedCornerShape(11.dp)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}


@Composable
internal fun InputDialog(title: Int, button: Int, dismiss: () -> Unit, enabled: Boolean, confirm: () -> Unit, content: @Composable () -> Unit) {
    FlowAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        },
        confirmButton = { DialogAction(button, confirm, enabled) },
        dismissButton = { DialogAction(R.string.flow_cancel, dismiss) },
    )
}

@Composable
internal fun DialogAction(label: Int, click: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = click, enabled = enabled,
        shapes = ButtonDefaults.shapes(RoundedCornerShape(20.dp), RoundedCornerShape(10.dp)),
    ) { Text(stringResource(label)) }
}

@Composable
internal fun FlowAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val opacity = remember { Animatable(0f) }
    val scale = remember { Animatable(.91f) }
    LaunchedEffect(Unit) { opacity.animateTo(1f, motion.defaultEffectsSpec()) }
    LaunchedEffect(Unit) { scale.animateTo(1f, motion.defaultSpatialSpec()) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.graphicsLayer {
            alpha = opacity.value
            scaleX = scale.value
            scaleY = scale.value
        },
        title = {
            val provider = LocalView.current.parent as? DialogWindowProvider
            SideEffect { provider?.window?.setWindowAnimations(R.style.FlowDialogMotion) }
            title()
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}


@Composable
private fun ImportSquare(icon: Int, label: Int, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = click,
        modifier = Modifier.size(106.dp),
        shapes = ButtonDefaults.shapes(RoundedCornerShape(23.dp), RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.filledTonalButtonColors(colors.secondaryContainer, colors.onSecondaryContainer),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowIcon(icon, 26.dp, LocalContentColor.current)
            Text(stringResource(label), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = colors.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable
private fun SheetAction(icon: Int, label: Int, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).flowPress(interaction).clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHighest).clickable(interaction, ripple(), onClick = click)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowIcon(icon, 20.dp, colors.secondary)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditorSheet(title: Int, button: Int, dismiss: () -> Unit, enabled: Boolean, save: () -> Unit, fields: @Composable () -> Unit) {
    FlowSheet(stringResource(title), dismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp)) { fields() }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            DialogAction(R.string.flow_cancel, dismiss)
            Button(
                onClick = save, enabled = enabled,
                shapes = ButtonDefaults.shapes(RoundedCornerShape(19.dp), RoundedCornerShape(10.dp)),
            ) { Text(stringResource(button)) }
        }
    }
}

@Composable
private fun ProfileEditor(server: FlowServer, actions: FlowActivity, dismiss: () -> Unit) {
    val original = server.profile
    val custom = original.configType == EConfigType.CUSTOM
    val composite = original.configType in setOf(EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
    var name by rememberSaveable(server.id) { mutableStateOf(original.remarks) }
    var host by rememberSaveable(server.id) { mutableStateOf(original.server.orEmpty()) }
    var port by rememberSaveable(server.id) { mutableStateOf(original.serverPort.orEmpty()) }
    var credential by rememberSaveable(server.id) { mutableStateOf(original.password.orEmpty()) }
    var advanced by rememberSaveable(server.id) { mutableStateOf(custom || composite) }
    var json by rememberSaveable(server.id) { mutableStateOf(if (custom) "" else JsonUtil.toJson(original)) }
    var sourceLoaded by rememberSaveable(server.id) { mutableStateOf(!custom) }
    LaunchedEffect(server.id) {
        if (!sourceLoaded) {
            json = withContext(Dispatchers.IO) { MmkvManager.decodeServerRaw(server.id).orEmpty() }
            sourceLoaded = true
        }
    }
    val export = remember(advanced, json, custom) {
        if (advanced && !custom) runCatching { JsonUtil.fromJson(json, ProfileItem::class.java) }.getOrNull() else null
    }
    val valid = when {
        custom -> sourceLoaded && runCatching { com.google.gson.JsonParser.parseString(json).isJsonObject }.getOrDefault(false)
        advanced -> export != null
        else -> host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535
    }
    EditorSheet(R.string.flow_edit, R.string.flow_save, dismiss, valid, {
        val edited = if (advanced && !custom) {
            (export ?: original).copy(configType = original.configType, subscriptionId = original.subscriptionId,
                addedTime = original.addedTime)
        } else original.copy(remarks = name.trim(), server = host.trim(), serverPort = port.trim(),
            password = credential.ifBlank { original.password })
        actions.saveProfile(server, edited, if (custom) json else null)
        dismiss()
    }) {
        if (!advanced || custom) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.flow_name)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        if (!advanced) {
            OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.flow_host)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(port, { port = it }, label = { Text(stringResource(R.string.flow_port)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
            if (original.password != null) {
                OutlinedTextField(credential, { credential = it }, label = { Text(stringResource(R.string.flow_credential)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation())
            }
        } else {
            OutlinedTextField(json, { json = it }, label = { Text(stringResource(R.string.flow_config_json)) },
                modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10, shape = RoundedCornerShape(16.dp))
        }
        if (!custom && !composite) {
            ToggleRow(R.string.flow_config_json, advanced, {
                if (it) json = JsonUtil.toJson(original.copy(remarks = name, server = host, serverPort = port, password = credential))
                advanced = it
            })
        }
    }
}

