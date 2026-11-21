package com.v2ray.ang.ui.flow

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.enums.Language
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.LogcatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

@Composable
internal fun ToggleRow(label: Int, selected: Boolean, toggle: (Boolean) -> Unit, supporting: String? = null) {
    ToolRow(stringResource(label), supporting, selected, toggle)
}

@Composable
private fun ToolRow(label: String, supporting: String?, selected: Boolean, toggle: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 51.dp).flowPress(interaction).clip(RoundedCornerShape(14.dp))
            .toggleable(selected, interaction, ripple(), role = Role.Switch, onValueChange = toggle)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!supporting.isNullOrBlank()) Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(selected, onCheckedChange = null)
    }
}

internal enum class FlowSettingsPage {
    LANGUAGE, ROUTING, LOGS, ABOUT, ADVANCED, BACKUP, GEODATA,
}

@Composable
internal fun FlowSettingsDestination(
    page: FlowSettingsPage,
    actions: FlowActivity,
    back: () -> Unit,
    open: (FlowSettingsPage) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider(page.name) {
        when (page) {
            FlowSettingsPage.LANGUAGE -> SettingsScreen(stringResource(R.string.flow_language), back) {
                val selected = remember { MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: Language.AUTO.code }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (language in listOf(Language.AUTO, Language.RUSSIAN, Language.ENGLISH)) {
                        val label = when (language) {
                            Language.RUSSIAN -> "Русский"
                            Language.ENGLISH -> "English"
                            else -> stringResource(R.string.flow_language_system)
                        }
                        Choice(label, selected == language.code) {
                            if (selected != language.code) actions.changeLanguage(language)
                        }
                    }
                }
            }
            FlowSettingsPage.ROUTING -> AppRoutingPage(actions, back)
            FlowSettingsPage.LOGS -> LogsPage(back)
            FlowSettingsPage.ABOUT -> AboutPage(back)
            FlowSettingsPage.ADVANCED -> AdvancedPage(actions, back, open)
            FlowSettingsPage.BACKUP -> BackupPage(actions, back)
            FlowSettingsPage.GEODATA -> GeodataPage(actions, back)
        }
    }
}

@Composable
private fun SettingsScreen(
    title: String,
    back: () -> Unit,
    toolbar: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .navigationBarsPadding().imePadding().padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(bottom = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionSquare(R.drawable.flow_arrow, R.string.flow_back, 44.dp, colors.surfaceContainerHigh,
                    colors.onSurface, back, rotation = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 180f else 0f)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                toolbar()
            }
            body()
        }
    }
}

private enum class AppRoutingMode(val label: Int, val icon: Int) {
    ALL(R.string.flow_route_all, R.drawable.flow_symbol_dns),
    ONLY_SELECTED(R.string.flow_route_only, R.drawable.flow_shield),
    EXCLUDE_SELECTED(R.string.flow_route_exclude, R.drawable.flow_symbol_swap_vert),
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AppRoutingPage(actions: FlowActivity, back: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val apps by produceState<List<AppInfo>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            AppManagerUtil.loadNetworkAppList(context).filterNot { it.packageName == context.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, AppInfo::appName))
        }
    }
    var selected by rememberSaveable {
        mutableStateOf(ArrayList(MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).orEmpty()))
    }
    var mode by rememberSaveable {
        mutableStateOf(when {
            !MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) -> AppRoutingMode.ALL
            MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS) -> AppRoutingMode.EXCLUDE_SELECTED
            else -> AppRoutingMode.ONLY_SELECTED
        })
    }
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedPackages = remember(selected) { selected.toSet() }
    val (selectedApps, otherApps) = remember(query, includeSystem, apps, selectedPackages) {
        val text = query.trim()
        apps.orEmpty().filter { app ->
            (includeSystem || !app.isSystemApp || app.packageName in selectedPackages) &&
                (app.appName.contains(text, true) || app.packageName.contains(text, true))
        }.partition { it.packageName in selectedPackages }
    }
    val unselectedRussianApps = remember(apps, selectedPackages) {
        apps.orEmpty().map(AppInfo::packageName)
            .filter { it in russianServicePackages && it !in selectedPackages }
    }

    SettingsScreen(stringResource(R.string.flow_split), back, toolbar = {
        ActionSquare(R.drawable.flow_status_on, R.string.flow_save, 44.dp, colors.primary, colors.onPrimary, {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, mode != AppRoutingMode.ALL)
            if (mode != AppRoutingMode.ALL) {
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, mode == AppRoutingMode.EXCLUDE_SELECTED)
            }
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, selected.toMutableSet())
            actions.restartForSettings()
            back()
        }, enabled = mode != AppRoutingMode.ONLY_SELECTED || selected.isNotEmpty())
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppRoutingMode.entries.forEach { choice ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val chosen = mode == choice
                    val radius = flowChoiceRadius(chosen, 14.dp, 30.dp)
                    val fill by animateColorAsState(if (chosen) colors.primaryContainer else colors.surfaceContainerHigh,
                        MaterialTheme.motionScheme.fastEffectsSpec(), label = "routing button color")
                    val label = stringResource(choice.label)
                    FilledIconButton(
                        onClick = { mode = choice },
                        modifier = Modifier.size(60.dp).semantics {
                            this.selected = chosen
                            contentDescription = label
                        },
                        shapes = IconButtonDefaults.shapes(RoundedCornerShape(radius), RoundedCornerShape(11.dp)),
                        colors = IconButtonDefaults.filledIconButtonColors(fill,
                            if (chosen) colors.onPrimaryContainer else colors.onSurfaceVariant),
                    ) {
                        FlowIcon(choice.icon, 24.dp, LocalContentColor.current)
                    }
                    Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val density = LocalDensity.current
        val listState = rememberLazyListState()
        var controlsHeight by remember { mutableIntStateOf(0) }
        val inset = if (controlsHeight == 0) 162.dp else with(density) { controlsHeight.toDp() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().flowHeaderFade(inset, 24.dp, visible = listState.canScrollBackward),
                state = listState,
                contentPadding = PaddingValues(top = inset, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (apps == null) item("loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else if (selectedApps.isEmpty() && otherApps.isEmpty()) item("empty") {
                    EmptyMessage(R.string.flow_no_apps)
                }
                val appRow: @Composable LazyItemScope.(AppInfo) -> Unit = { app ->
                    val mark = app.packageName in selectedPackages
                    val radius = flowChoiceRadius(mark, 12.dp, 25.dp)
                    val interaction = remember { MutableInteractionSource() }
                    val icon = remember(app) { app.appIcon.toBitmap(width = 104, height = 104).asImageBitmap() }
                    Row(
                        Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                            .fillMaxWidth().heightIn(min = 64.dp).flowPress(interaction)
                            .clip(RoundedCornerShape(radius))
                            .background(if (mark) colors.secondaryContainer else colors.surfaceContainer)
                            .toggleable(mark, interaction, ripple(), role = Role.Checkbox) { picked ->
                                selected = ArrayList(if (picked) selectedPackages + app.packageName else selectedPackages - app.packageName)
                            }.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val checkColor by animateColorAsState(if (mark) colors.primary else colors.surfaceContainerHighest,
                            MaterialTheme.motionScheme.fastEffectsSpec(), label = "app selected")
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(checkColor),
                            contentAlignment = Alignment.Center) {
                            val check by animateFloatAsState(if (mark) 1f else 0f,
                                MaterialTheme.motionScheme.fastSpatialSpec(), label = "app checkmark")
                            Box(Modifier.graphicsLayer {
                                alpha = check.coerceIn(0f, 1f)
                                scaleX = .55f + alpha * .45f
                                scaleY = scaleX
                            }) {
                                FlowIcon(R.drawable.flow_status_on, 16.dp, colors.onPrimary)
                            }
                        }
                    }
                }
                items(selectedApps, key = AppInfo::packageName, itemContent = appRow)
                if (selectedApps.isNotEmpty() && otherApps.isNotEmpty()) item("routing-divider") {
                    HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = colors.outlineVariant)
                }
                items(otherApps, key = AppInfo::packageName, itemContent = appRow)
            }
            Column(Modifier.fillMaxWidth().onSizeChanged { controlsHeight = it.height }.padding(bottom = 22.dp)) {
                FlowSearchField(query, { query = it }, R.string.flow_find_app)
                val systemLabel = stringResource(if (includeSystem) R.string.flow_show_user_apps else R.string.flow_show_system_apps)
                val systemFill by animateColorAsState(if (includeSystem) colors.secondaryContainer else colors.surfaceContainerHigh,
                    MaterialTheme.motionScheme.defaultEffectsSpec(), label = "system apps toggle color")
                FilledTonalButton(
                    onClick = { includeSystem = !includeSystem },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp).semantics { contentDescription = systemLabel },
                    shapes = ButtonDefaults.shapes(RoundedCornerShape(17.dp), RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = systemFill,
                        contentColor = if (includeSystem) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    ),
                ) {
                    FlowIcon(R.drawable.flow_android, 18.dp, LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    FlowToggleContent(includeSystem, Modifier.clearAndSetSemantics {}, label = "system apps label") {
                        Text(stringResource(if (it) R.string.flow_show_user_apps else R.string.flow_show_system_apps))
                    }
                }
                FilledTonalButton(
                    onClick = {
                        selected = ArrayList(selectedPackages + unselectedRussianApps)
                        if (query.isNotEmpty()) query = ""
                        listState.requestScrollToItem(0)
                    },
                    enabled = unselectedRussianApps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(RoundedCornerShape(17.dp), RoundedCornerShape(10.dp)),
                ) {
                    FlowIcon(R.drawable.flow_add, 18.dp, LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.flow_add_russian_apps))
                }
            }
        }
    }
}

@Composable
private fun LogsPage(back: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var refresh by remember { mutableIntStateOf(0) }
    val logs by produceState<List<String>>(emptyList(), refresh) {
        value = withContext(Dispatchers.IO) { LogcatViewModel().apply { loadLogcat() }.getAll().take(1500) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, logs) { logs.filter { it.contains(query, ignoreCase = true) } }
    SettingsScreen(stringResource(R.string.flow_logs), back, toolbar = {
        ActionSquare(R.drawable.flow_symbol_refresh, R.string.flow_refresh_logs, 44.dp, colors.surfaceContainerHigh, colors.onSurface, { refresh++ })
        ActionSquare(R.drawable.flow_symbol_content_paste, R.string.flow_copy, 44.dp, colors.secondaryContainer, colors.onSecondaryContainer,
            { Utils.setClipboard(context, filtered.joinToString("\n")) }, filtered.isNotEmpty())
    }) {
        FlowSearchField(query, { query = it }, R.string.flow_search_logs)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
            itemsIndexed(filtered) { _, log ->
                Text(log, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AboutPage(back: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val version by produceState("") { value = withContext(Dispatchers.IO) { CoreNativeManager.getLibVersion() } }
    SettingsScreen(stringResource(R.string.flow_about), back) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.flow_logo), contentDescription = null, modifier = Modifier.size(104.dp))
            Text("Flow", style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.flow_app_version, BuildConfig.VERSION_NAME), color = colors.onSurfaceVariant)
            Text(stringResource(R.string.flow_developer, "iriskaaf"), style = MaterialTheme.typography.titleMedium)
            FlowUpdatePanel()
            Text(stringResource(R.string.flow_components), Modifier.fillMaxWidth().padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium)
            val components = listOf(
                if (version.isBlank()) "Xray" else stringResource(R.string.flow_core_version, version),
                "v2rayNG 2.1.8", "AndroidLibXrayLite", "HEV SOCKS5 Tunnel", "Jetpack Compose · Material 3 Expressive",
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                components.forEach { component ->
                    Text(component, Modifier.fillMaxWidth().background(colors.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private data class CoreOption(
    val key: String,
    val title: String,
    val summary: String,
    val default: String,
    val switch: Boolean,
    val labels: List<String>,
    val values: List<String>,
    val category: String,
)

private fun coreOptions(context: Context): List<CoreOption> = buildList {
    val namespace = "http://schemas.android.com/apk/res/android"
    val hidden = setOf(
        AppConfig.PREF_LANGUAGE, "pref_ui_mode_night", "pref_double_column_display",
        "pref_group_all_display", "pref_start_scan_immediate", "pref_confirm_remove",
        "pref_auto_remove_invalid_after_test", "pref_auto_sort_after_test",
    )
    context.resources.getXml(R.xml.pref_settings).use { parser ->
        fun text(attribute: String): String {
            val id = parser.getAttributeResourceValue(namespace, attribute, 0)
            return if (id != 0) context.getString(id) else parser.getAttributeValue(namespace, attribute).orEmpty()
        }
        fun values(attribute: String): List<String> {
            val id = parser.getAttributeResourceValue(namespace, attribute, 0)
            return if (id != 0) context.resources.getStringArray(id).toList() else emptyList()
        }
        var group = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val type = parser.name
            if (type == "PreferenceCategory") group = text("title")
            if (type !in setOf("CheckBoxPreference", "ListPreference", "EditTextPreference", "SwitchPreferenceCompat")) continue
            val key = text("key")
            if (key.isBlank() || key in hidden) continue
            add(CoreOption(key, text("title"), text("summary").takeUnless { it == "%s" }.orEmpty(),
                text("defaultValue"), type == "CheckBoxPreference" || type == "SwitchPreferenceCompat",
                values("entries"), values("entryValues"), group))
        }
    }
}

@Composable
private fun AdvancedPage(actions: FlowActivity, back: () -> Unit, open: (FlowSettingsPage) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val options = remember(context) { coreOptions(context) }
    val entries = remember { mutableStateMapOf<String, String>() }
    var changed by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var routes by rememberSaveable { mutableStateOf(false) }
    var routeJson by rememberSaveable { mutableStateOf("") }
    var routeError by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }

    fun read(option: CoreOption): String = entries[option.key] ?: if (option.switch) {
        MmkvManager.decodeSettingsBool(option.key, option.default == "true").toString()
    } else MmkvManager.decodeSettingsString(option.key, option.default).orEmpty()

    fun write(option: CoreOption, value: String) {
        if (option.switch) MmkvManager.encodeSettings(option.key, value == "true")
        else MmkvManager.encodeSettings(option.key, value)
        entries[option.key] = value
        changed = true
    }

    fun applyChanges() {
        if (changed) {
            actions.restartForSettings()
            changed = false
        }
    }

    fun close() {
        when {
            routes -> routes = false
            selectedKey != null -> selectedKey = null
            else -> { applyChanges(); back() }
        }
    }

    BackHandler { close() }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = if (routes) "routing" else selectedKey,
        transitionSpec = {
            (fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it / 16 })
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
        },
        label = "advanced settings page",
    ) { key ->
        val option = options.firstOrNull { it.key == key }
        when {
            key == "routing" -> SettingsForm(stringResource(R.string.flow_routing), ::close,
                R.string.flow_save, routeJson.isNotBlank(), {
                    if (SettingsManager.resetRoutingRulesets(routeJson)) {
                        changed = true
                        routes = false
                    } else routeError = true
                }) {
                OutlinedTextField(routeJson, { routeJson = it; routeError = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.flow_config_json)) }, minLines = 7, maxLines = 16,
                    isError = routeError, supportingText = {
                        if (routeError) Text(stringResource(R.string.flow_invalid_settings))
                    })
            }
            option != null && option.values.isNotEmpty() && option.labels.size == option.values.size ->
                SettingsScreen(option.title, ::close) {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(option.labels) { index, label ->
                            Choice(label, option.values[index] == read(option)) {
                                write(option, option.values[index])
                                close()
                            }
                        }
                    }
                }
            option != null -> {
                var value by rememberSaveable(key) { mutableStateOf(read(option)) }
                SettingsForm(option.title, ::close, R.string.flow_save, true, {
                    write(option, value.trim())
                    close()
                }) {
                    OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text(option.title) },
                        visualTransformation = if ("password" in option.key) PasswordVisualTransformation() else VisualTransformation.None)
                }
            }
            else -> SettingsScreen(stringResource(R.string.flow_advanced), ::close) {
                FlowSearchField(query, { query = it }, R.string.flow_search_settings)
                val shown = remember(options, query) {
                    options.filter { it.title.contains(query, true) || it.category.contains(query, true) }
                        .groupBy(CoreOption::category)
                }
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp), state = listState) {
                    if (query.isBlank()) item {
                        Column {
                            MenuAction(R.string.flow_routing) {
                                routeJson = JsonUtil.toJson(MmkvManager.decodeRoutingRulesets().orEmpty())
                                routeError = false
                                routes = true
                            }
                            MenuAction(R.string.title_configuration_backup_restore) {
                                applyChanges()
                                open(FlowSettingsPage.BACKUP)
                            }
                            MenuAction(R.string.flow_geodata) {
                                applyChanges()
                                open(FlowSettingsPage.GEODATA)
                            }
                        }
                    }
                    for ((category, section) in shown) {
                        item(key = "section:$category") {
                            Text(category, Modifier.padding(top = 14.dp, bottom = 5.dp),
                                color = colors.secondary, style = MaterialTheme.typography.titleSmall)
                        }
                        items(section, key = { it.key }) { setting ->
                            val value = read(setting)
                            if (setting.switch) ToolRow(setting.title, setting.summary, value == "true") { write(setting, it.toString()) }
                            else {
                                val interaction = remember { MutableInteractionSource() }
                                Column(Modifier.fillMaxWidth().heightIn(min = 50.dp).flowPress(interaction)
                                    .clip(RoundedCornerShape(14.dp)).clickable(interaction, ripple()) { selectedKey = setting.key }
                                    .padding(horizontal = 8.dp, vertical = 7.dp)) {
                                    Text(setting.title, style = MaterialTheme.typography.bodyLarge)
                                    val display = if ("password" in setting.key && value.isNotBlank()) "••••"
                                        else setting.labels.getOrNull(setting.values.indexOf(value)) ?: value.ifBlank { setting.summary }
                                    if (display.isNotBlank()) Text(display, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsForm(
    title: String, back: () -> Unit, confirmLabel: Int, enabled: Boolean, confirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsScreen(title, back) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Button(
                onClick = confirm, enabled = enabled, modifier = Modifier.align(Alignment.End),
                shapes = ButtonDefaults.shapes(RoundedCornerShape(19.dp), RoundedCornerShape(11.dp)),
            ) { Text(stringResource(confirmLabel)) }
        }
    }
}

private enum class BackupMode { RESTORE_LOCAL, UPLOAD, DOWNLOAD }

@Composable
private fun BackupPage(actions: FlowActivity, back: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf<BackupMode?>(null) }
    val closeEditor = { mode = null }
    BackHandler(mode != null, onBack = closeEditor)
    val motion = MaterialTheme.motionScheme
    AnimatedContent(mode, transitionSpec = {
        fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
    }, label = "backup page") { current ->
        when (current) {
            null -> SettingsScreen(stringResource(R.string.title_configuration_backup_restore), back) {
                MenuAction(R.string.flow_backup_file) { actions.exportBackup() }
                MenuAction(R.string.flow_restore_file) { mode = BackupMode.RESTORE_LOCAL }
                MenuAction(R.string.flow_backup_webdav) { mode = BackupMode.UPLOAD }
                MenuAction(R.string.flow_restore_webdav) { mode = BackupMode.DOWNLOAD }
            }
            BackupMode.RESTORE_LOCAL -> SettingsForm(stringResource(R.string.flow_restore_file), closeEditor,
                R.string.flow_continue, true, {
                    closeEditor()
                    actions.importBackup()
                }) { Text(stringResource(R.string.flow_restore_notice)) }
            BackupMode.UPLOAD, BackupMode.DOWNLOAD -> {
                val old = remember { MmkvManager.decodeWebDavConfig() }
                var url by rememberSaveable { mutableStateOf(old?.baseUrl.orEmpty()) }
                var username by rememberSaveable { mutableStateOf(old?.username.orEmpty()) }
                var password by remember { mutableStateOf(old?.password.orEmpty()) }
                var path by rememberSaveable { mutableStateOf(old?.remoteBasePath ?: "/") }
                val uploading = current == BackupMode.UPLOAD
                val label = if (uploading) R.string.flow_backup_webdav else R.string.flow_restore_webdav
                SettingsForm(stringResource(label), closeEditor, R.string.flow_continue, Utils.isValidUrl(url.trim()), {
                    closeEditor()
                    actions.webDavBackup(WebDavConfig(url.trim(), username, password, path.trim(), old?.timeoutSeconds ?: 30L), uploading)
                }) {
                    if (!uploading) Text(stringResource(R.string.flow_restore_notice))
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.flow_url)) })
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.flow_username)) })
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.flow_credential)) }, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.flow_webdav_path)) })
                }
            }
        }
    }
}

@Composable
private fun GeodataPage(actions: FlowActivity, back: () -> Unit) {
    var source by rememberSaveable {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first())
    }
    SettingsForm(stringResource(R.string.flow_geodata), back, R.string.flow_refresh_logs, true, {
        MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, source)
        actions.refreshGeodata()
        back()
    }) {
        for (choice in AppConfig.GEO_FILES_SOURCES) Choice(choice, source == choice) { source = choice }
    }
}

