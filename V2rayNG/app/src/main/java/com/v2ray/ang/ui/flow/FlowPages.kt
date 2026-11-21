package com.v2ray.ang.ui.flow

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.v2ray.ang.R
import com.v2ray.ang.handler.FlowPreferences
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServersPage(
    modifier: Modifier,
    bottomInset: Dp,
    profiles: FlowProfiles,
    search: String,
    searchChanged: (String) -> Unit,
    sorted: Boolean,
    toggleSort: () -> Unit,
    compact: Boolean,
    working: Boolean,
    pinging: Boolean,
    pingTarget: String?,
    add: () -> Unit,
    reorder: () -> Unit,
    select: (String) -> Unit,
    details: (FlowServer) -> Unit,
    favorite: (FlowServer) -> Unit,
    refresh: (FlowGroup) -> Unit,
    subscriptionOptions: (FlowGroup) -> Unit,
    ping: (List<String>, String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val keyword = search.trim()
    var listView by remember { mutableStateOf(MmkvManager.decodeSettingsBool(FlowPreferences.SERVER_LIST_VIEW)) }
    val listState = rememberLazyGridState()
    val viewProgress = animateFloatAsState(if (listView) 1f else 0f,
        motion.defaultSpatialSpec(), label = "server card reflow")
    val refreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val changing = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val revealRowLimit = LocalConfiguration.current.screenHeightDp / 90 + 3
    var headerHeight by remember { mutableIntStateOf(0) }
    val topInset = if (headerHeight == 0) 160.dp else with(density) { headerHeight.toDp() }
    val topThreshold = with(density) { 8.dp.roundToPx() }
    val atTop by remember(listState, topThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= topThreshold }
    }
    val headerBottomPadding by animateDpAsState(if (atTop) 4.dp else 28.dp, motion.fastSpatialSpec(),
        label = "server header spacing")
    var collapsed by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val filtered by produceState(Triple(keyword, sorted, profiles.groups), profiles.groups, keyword, sorted) {
        val matches = withContext(Dispatchers.Default) {
            if (keyword.isEmpty() && !sorted) return@withContext profiles.groups
            profiles.groups.mapNotNull { group ->
                val matched = if (keyword.isEmpty()) group.servers else group.servers.filter { server ->
                    server.sectionLabel == null && (server.name.contains(keyword, true) || server.protocol.contains(keyword, true) ||
                        server.serverDescription.orEmpty().contains(keyword, true) ||
                        server.profile.server.orEmpty().contains(keyword, true) || server.country.orEmpty().contains(keyword, true))
                }
                if (matched.isEmpty() && keyword.isNotEmpty()) null else group.copy(servers = if (sorted) {
                    matched.sortWithinSections()
                } else matched)
            }
        }
        if (value.second != sorted && value.first == keyword) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        value = Triple(keyword, sorted, matches)
    }
    val (appliedKeyword, _, groups) = filtered
    var previousKeyword by rememberSaveable { mutableStateOf(appliedKeyword) }
    LaunchedEffect(appliedKeyword) {
        if (appliedKeyword != previousKeyword) {
            listState.requestScrollToItem(0)
            previousKeyword = appliedKeyword
        }
    }
    val groupOrder = remember(groups) { groups.map(FlowGroup::id) }
    var previousOrder by remember { mutableStateOf(groupOrder) }
    LaunchedEffect(groupOrder) {
        if (previousOrder != groupOrder && previousOrder.toSet() == groupOrder.toSet()) {
            listState.requestScrollToItem(0)
        }
        previousOrder = groupOrder
    }
    val fullGroups = remember(profiles.groups) { profiles.groups.associateBy(FlowGroup::id) }
    val cellsByGroup = remember(groups, listView) { groups.associate { it.id to it.servers.cells(listView) } }

    PullToRefreshBox(
        isRefreshing = pinging && pingTarget == null,
        onRefresh = { ping(profiles.servers.map(FlowServer::id), null) },
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState, isRefreshing = pinging && pingTarget == null,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = topInset - 8.dp).zIndex(2f),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().flowHeaderFade(topInset, 40.dp, visible = !atTop),
            state = listState,
            contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
        ) {
            if (groups.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyMessage(if (keyword.isEmpty()) R.string.flow_no_servers else R.string.flow_no_matches,
                    if (keyword.isEmpty()) add else null)
            }
            for (group in groups) {
                val expanded = group.id !in collapsed || keyword.isNotEmpty()
                val animation = changing[group.id]?.takeIf { keyword.isEmpty() }
                val cells = cellsByGroup[group.id].orEmpty()
                val visibleCells = if (animation != null) cells.take(revealRowLimit * if (listView) 1 else 2) else cells
                item(key = "header:${group.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    SubscriptionHeader(
                        group, expanded, cells.isNotEmpty(), animation, working, keyword.isNotEmpty(),
                        loading = pinging && (pingTarget == null || pingTarget == group.id),
                        refresh = { refresh(group) },
                        toggle = {
                            val progress = changing[group.id]
                                ?: Animatable(if (expanded) 1f else 0f).also { changing[group.id] = it }
                            collapsed = ArrayList(if (expanded) collapsed + group.id else collapsed.filterNot { it == group.id })
                            scope.launch {
                                progress.animateTo(if (expanded) 0f else 1f, motion.defaultSpatialSpec())
                                if (changing[group.id] === progress) changing.remove(group.id)
                            }
                        },
                        ping = { ping(fullGroups[group.id]?.servers.orEmpty().filter { it.sectionLabel == null }.map(FlowServer::id), group.id) },
                        edit = { subscriptionOptions(group) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (expanded || animation != null) {
                    items(
                        visibleCells,
                        key = { "server:${it.server.id}" },
                        span = { GridItemSpan(if (it.slot.fullSpan) maxLineSpan else 1) },
                        contentType = { if (it.server.sectionLabel == null) "server" else "divider" },
                    ) { cell ->
                        val movement = if (animation == null) Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ) else Modifier.subscriptionReveal(animation)
                        val server = cell.server
                        Box(
                            movement.fillMaxWidth().clip(cell.shape).background(colors.surfaceContainerLow),
                        ) {
                            val inside = Modifier.fillMaxWidth(if (cell.slot == ServerCellSlot.HALF_ROW) .5f else 1f)
                                .padding(
                                    start = if (cell.slot == ServerCellSlot.END) 4.dp else 14.dp,
                                    end = if (cell.slot == ServerCellSlot.START || cell.slot == ServerCellSlot.HALF_ROW) 4.dp else 14.dp,
                                    bottom = if (cell.lastRow) 10.dp else 7.dp,
                                )
                            val section = server.sectionLabel
                            if (section != null) {
                                Box(inside) { ServerSection(section) { details(server) } }
                            } else {
                                ServerCard(
                                    inside.graphicsLayer {
                                        val distance = abs(viewProgress.value - if (listView) 1f else 0f).coerceIn(0f, 1f)
                                        scaleX = 1f - distance * (if (listView) .12f else .08f)
                                        scaleY = 1f - distance * .07f
                                        alpha = 1f - distance * .24f
                                        transformOrigin = TransformOrigin(if (listView) .25f else .5f, .25f)
                                    },
                                    server, server.id == profiles.selected, listView,
                                    { select(server.id) }, { details(server) }, { favorite(server) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(1f).onSizeChanged { headerHeight = it.height }
                .padding(bottom = headerBottomPadding.coerceAtLeast(0.dp)),
        ) {
            PageTitle(
                stringResource(R.string.flow_all_servers), compact,
                stringResource(R.string.flow_subscriptions_count, profiles.groups.count { it.subscription != null }, profiles.servers.size),
            ) {
                ActionSquare(R.drawable.flow_add, R.string.flow_add, 52.dp, colors.primary, colors.onPrimary, add, !working)
            }
            Spacer(Modifier.height(5.dp))
            ServerSearch(search, searchChanged, atTop, sorted, toggleSort,
                profiles.groups.count { it.subscription != null } > 1, reorder)
            AnimatedVisibility(
                atTop,
                enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Box(Modifier.padding(top = 12.dp)) {
                    ServerViewSwitch(listView) { mode ->
                        if (mode != listView) {
                            listView = mode
                            MmkvManager.encodeSettings(FlowPreferences.SERVER_LIST_VIEW, mode)
                        }
                    }
                }
            }
            if (working) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

private enum class ServerCellSlot(val fullSpan: Boolean) { FULL(true), START(false), END(false), HALF_ROW(true) }

private data class ServerCell(val server: FlowServer, val slot: ServerCellSlot, val lastRow: Boolean) {
    val shape = RoundedCornerShape(
        bottomStart = if (lastRow && slot != ServerCellSlot.END) 22.dp else 0.dp,
        bottomEnd = if (lastRow && slot != ServerCellSlot.START) 22.dp else 0.dp,
    )
}

private fun List<FlowServer>.cells(listView: Boolean): List<ServerCell> {
    val rows = sectionRows(if (listView) 1 else 2)
    return rows.flatMapIndexed { index, row ->
        val last = index == rows.lastIndex
        when {
            listView || row.first().sectionLabel != null -> listOf(ServerCell(row.first(), ServerCellSlot.FULL, last))
            row.size == 1 -> listOf(ServerCell(row.first(), ServerCellSlot.HALF_ROW, last))
            else -> listOf(ServerCell(row[0], ServerCellSlot.START, last), ServerCell(row[1], ServerCellSlot.END, last))
        }
    }
}

private fun Modifier.subscriptionReveal(progress: Animatable<Float, AnimationVector1D>): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        val amount = progress.value.coerceIn(0f, 1f)
        val height = (child.height * amount).roundToInt()
        layout(child.width, height) {
            child.placeRelativeWithLayer(0, ((height - child.height) * .3f).roundToInt()) { alpha = amount }
        }
    }


private fun List<FlowServer>.sortWithinSections(): List<FlowServer> = buildList(size) {
    var start = 0
    val count = this@sortWithinSections.size
    for (index in 0..count) {
        if (index < count && this@sortWithinSections[index].sectionLabel == null) continue
        addAll(this@sortWithinSections.subList(start, index).sortedBy { it.delayMillis.takeIf { ms -> ms > 0 } ?: Long.MAX_VALUE })
        if (index < count) add(this@sortWithinSections[index])
        start = index + 1
    }
}

internal fun List<FlowServer>.sectionRows(columns: Int = 2): List<List<FlowServer>> = buildList {
    var start = 0
    val count = this@sectionRows.size
    for (index in 0..count) {
        if (index < count && this@sectionRows[index].sectionLabel == null) continue
        addAll(this@sectionRows.subList(start, index).chunked(columns))
        if (index < count) add(listOf(this@sectionRows[index]))
        start = index + 1
    }
}

@Composable
private fun ServerViewSwitch(listView: Boolean, change: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh).padding(4.dp),
    ) {
        val offset by animateDpAsState(if (listView) maxWidth / 2 else 0.dp, motion.defaultSpatialSpec(),
            label = "server view indicator")
        Box(Modifier.offset(x = offset).width(maxWidth / 2).fillMaxHeight()
            .clip(RoundedCornerShape(16.dp)).background(colors.primary))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            for (mode in listOf(false, true)) {
                val selected = listView == mode
                val interaction = remember { MutableInteractionSource() }
                val ink by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    motion.fastEffectsSpec(), label = "server view color")
                Row(
                    Modifier.weight(1f).fillMaxHeight().flowPress(interaction).clip(RoundedCornerShape(16.dp))
                        .selectable(selected, interaction, ripple(), role = Role.RadioButton, onClick = { change(mode) }),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowIcon(if (mode) R.drawable.flow_view_list else R.drawable.flow_view_grid, 17.dp, ink)
                    Text(stringResource(if (mode) R.string.flow_view_list else R.string.flow_view_grid),
                        style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ServerSection(label: String, options: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = interaction, indication = ripple(),
                onClick = options, onLongClick = options,
                onLongClickLabel = stringResource(R.string.flow_profile_options),
            ).padding(horizontal = 8.dp),
    ) {
        val labelWidth = maxWidth * .65f
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (label.isBlank()) 12.dp else 36.dp)
                .semantics { if (label.isNotBlank()) heading() },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
            if (label.isNotBlank()) {
                Text(label, Modifier.widthIn(max = labelWidth), style = MaterialTheme.typography.labelLarge,
                    color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
            }
        }
    }
}

@Composable
private fun ServerSearch(
    search: String, searchChanged: (String) -> Unit, atTop: Boolean,
    sorted: Boolean, toggleSort: () -> Unit, reorderEnabled: Boolean, reorder: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var opened by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hint = stringResource(R.string.flow_server_search)
    fun close() {
        focus.clearFocus()
        keyboard?.hide()
        opened = false
        searchChanged("")
    }
    BackHandler(opened || search.isNotEmpty()) { close() }
    LaunchedEffect(opened) {
        if (opened) {
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val expandedSort = atTop && !opened
        val toolbarWidth = maxWidth
        val sortWidth by animateDpAsState(
            if (expandedSort) (toolbarWidth * .34f).coerceIn(108.dp, 136.dp) else 52.dp,
            motion.defaultSpatialSpec(), label = "sort width",
        )
        val sortIconWidth by animateDpAsState(if (expandedSort) 43.dp else 52.dp,
            motion.defaultSpatialSpec(), label = "sort icon position")
        val sortTextAlpha by animateFloatAsState(if (expandedSort) 1f else 0f,
            motion.fastEffectsSpec(), label = "sort text")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f).height(52.dp)) {
                val searchProgress by animateFloatAsState(if (opened || atTop) 1f else 0f,
                    motion.defaultSpatialSpec(), label = "search width")
                val width = 52.dp + (maxWidth - 52.dp).coerceAtLeast(0.dp) * searchProgress.coerceIn(0f, 1f)
                val corner by animateDpAsState(if (opened || atTop) 26.dp else 18.dp,
                    motion.fastSpatialSpec(), label = "search shape")
                val interaction = remember { MutableInteractionSource() }
                val style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface)
                val click = if (opened) Modifier else Modifier
                    .semantics { contentDescription = hint }
                    .flowPress(interaction)
                    .clickable(interaction, ripple(), role = Role.Button) { opened = true }
                Box(
                    Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
                        .background(if (!opened && search.isNotBlank()) colors.secondaryContainer else colors.surfaceContainerHigh)
                        .then(click),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.size(46.dp, 52.dp), contentAlignment = Alignment.Center) {
                        FlowIcon(R.drawable.flow_symbol_search, 21.dp, colors.onSurfaceVariant)
                    }
                    if (opened) {
                        BasicTextField(
                            value = search, onValueChange = searchChanged, singleLine = true, textStyle = style,
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxSize().padding(start = 46.dp, end = 44.dp)
                                .focusRequester(requester).semantics { contentDescription = hint },
                            decorationBox = { input ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                    if (search.isEmpty()) Text(hint, style = style, color = colors.onSurfaceVariant, maxLines = 1)
                                    input()
                                }
                            },
                        )
                        IconButton(
                            onClick = ::close,
                            modifier = Modifier.align(Alignment.CenterEnd).size(44.dp),
                        ) {
                            FlowIcon(R.drawable.flow_symbol_close, 20.dp, colors.onSurfaceVariant,
                                stringResource(R.string.flow_clear_search))
                        }
                    } else if (searchProgress > .05f) {
                        Text(hint, Modifier.padding(start = 46.dp, end = 8.dp).clearAndSetSemantics {}
                            .graphicsLayer { alpha = searchProgress.coerceIn(0f, 1f) },
                            style = style, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            AnimatedVisibility(
                visible = !opened,
                enter = expandHorizontally(motion.defaultSpatialSpec(), expandFrom = Alignment.End) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkHorizontally(motion.fastSpatialSpec(), shrinkTowards = Alignment.End) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Box(Modifier.padding(end = 8.dp)) {
                    ActionSquare(R.drawable.flow_symbol_swap_vert, R.string.flow_reorder_subscriptions, 52.dp,
                        colors.surfaceContainerHigh, colors.onSurfaceVariant, reorder, reorderEnabled && !opened)
                }
            }
            val sortInteraction = remember { MutableInteractionSource() }
            val sortPressed by sortInteraction.collectIsPressedAsState()
            val sortRadius by animateDpAsState(
                if (sortPressed) 12.dp else if (sorted) 26.dp else 18.dp,
                motion.fastSpatialSpec(), label = "sort shape",
            )
            val sortFill by animateColorAsState(if (sorted) colors.secondaryContainer else colors.surfaceContainerHigh,
                motion.fastEffectsSpec(), label = "sort color")
            val sortInk by animateColorAsState(if (sorted) colors.onSecondaryContainer else colors.onSurfaceVariant,
                motion.fastEffectsSpec(), label = "sort ink")
            val sortLabel = stringResource(if (sorted) R.string.flow_by_ping else R.string.flow_by_order)
            Row(
                Modifier.width(sortWidth.coerceAtLeast(52.dp)).height(52.dp).flowPress(sortInteraction)
                    .clip(RoundedCornerShape(sortRadius.coerceAtLeast(0.dp))).background(sortFill)
                    .semantics { contentDescription = sortLabel }
                    .clickable(sortInteraction, ripple(), role = Role.Button, onClick = toggleSort),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(sortIconWidth.coerceIn(40.dp, 52.dp)).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    FlowToggleContent(sorted, Modifier.size(26.dp), label = "sort icon") { byLatency ->
                        FlowIcon(if (byLatency) R.drawable.flow_sort_latency else R.drawable.flow_sort, 20.dp, sortInk)
                    }
                }
                if (sortTextAlpha > .02f && sortWidth > 64.dp) {
                    FlowToggleContent(sorted,
                        modifier = Modifier.weight(1f).padding(end = 8.dp).clipToBounds()
                            .graphicsLayer { alpha = sortTextAlpha.coerceIn(0f, 1f) }.clearAndSetSemantics {},
                        label = "sort order",
                    ) { byLatency ->
                        BasicText(
                            stringResource(if (byLatency) R.string.flow_by_ping else R.string.flow_by_order),
                            style = MaterialTheme.typography.labelMedium.copy(color = sortInk, letterSpacing = 0.sp),
                            autoSize = TextAutoSize.StepBased(9.sp, 13.sp, .5.sp),
                            softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable

internal fun FlowSearchField(
    query: String, change: (String) -> Unit, label: Int, modifier: Modifier = Modifier, clear: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val placeholder = stringResource(label)
    TextField(
        query, change, modifier = modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = placeholder },
        textStyle = MaterialTheme.typography.bodyMedium, singleLine = true, shape = RoundedCornerShape(26.dp),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { FlowIcon(R.drawable.flow_symbol_search, 20.dp, colors.onSurfaceVariant) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { if (clear != null) clear() else change("") }) {
                    FlowIcon(R.drawable.flow_symbol_close, 20.dp, colors.onSurfaceVariant, stringResource(R.string.flow_clear_search))
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh, unfocusedContainerColor = colors.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SubscriptionHeader(
    group: FlowGroup, expanded: Boolean, hasRows: Boolean, expansion: Animatable<Float, AnimationVector1D>?,
    working: Boolean, searching: Boolean, loading: Boolean,
    refresh: () -> Unit, toggle: () -> Unit, ping: () -> Unit, edit: () -> Unit, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val subscription = group.subscription?.subscription
    val progress = if (hasRows) expansion?.value?.coerceIn(0f, 1f) ?: if (expanded) 1f else 0f else 0f
    val corners = 22.dp * (1f - progress)
    val rotation by animateFloatAsState(if (expanded) 0f else -180f, motion.defaultSpatialSpec(), label = "subscription chevron")
    val count = stringResource(R.string.flow_server_count, group.serversCount)
    val locale = LocalConfiguration.current.locales[0]
    val updated = remember(subscription?.lastUpdated, locale) {
        subscription?.lastUpdated?.takeIf { it > 0 }?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it))
        }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp, 22.dp, corners, corners))
            .background(colors.surfaceContainerLow).padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interaction = remember { MutableInteractionSource() }
        Column(
            Modifier.weight(1f).flowPress(interaction).clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interaction, indication = ripple(), onClick = { if (!searching && hasRows) toggle() },
                    onLongClick = if (subscription != null) edit else null,
                    onLongClickLabel = stringResource(R.string.flow_subscription_options),
                ).padding(horizontal = 5.dp, vertical = 4.dp),
        ) {
            Text(group.title ?: stringResource(R.string.flow_local), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(count, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
            if (updated != null) {
                Text(updated, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
        ActionSquare(R.drawable.flow_speed, R.string.flow_ping_subscription, 48.dp, colors.surfaceContainerHigh,
            colors.secondary, ping, group.serversCount > 0, loading = loading)
        if (subscription != null) {
            ActionSquare(R.drawable.flow_symbol_refresh, R.string.flow_refresh, 48.dp, colors.surfaceContainerHigh,
                colors.secondary, refresh, !working && subscription.enabled)
        }
        ActionSquare(R.drawable.flow_expand, R.string.flow_expand, 48.dp, colors.surfaceContainerHigh,
            colors.onSurfaceVariant, toggle, hasRows && !searching, rotation)
    }
}

@Composable
private fun ServerCard(
    modifier: Modifier, server: FlowServer, selected: Boolean, listView: Boolean,
    select: () -> Unit, details: () -> Unit, favorite: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val foreground by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, motion.fastEffectsSpec())
    val supporting by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, motion.fastEffectsSpec())
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, motion.defaultEffectsSpec())
    val card = modifier.animateContentSize(motion.defaultSpatialSpec()).heightIn(min = if (listView) 64.dp else 156.dp)
        .flowPress(interaction).clip(RoundedCornerShape(15.dp)).background(background)
        .combinedClickable(
            interactionSource = interaction, indication = ripple(), onClick = select, onLongClick = details,
            onLongClickLabel = stringResource(R.string.flow_profile_options),
        )

    if (listView) {
        val inset = 12.dp
        Box(card) {
            Row(
                Modifier.align(Alignment.CenterStart).fillMaxWidth()
                    .padding(start = inset, end = inset + 94.dp, top = inset, bottom = inset),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { Flag(server.country, supporting) }
                ServerSummary(server, foreground, supporting, Modifier.weight(1f))
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = inset, end = inset).height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerSelectionMark(selected, foreground)
                ServerFavorite(server.favorite, supporting, favorite, compact = true)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = inset, bottom = inset).widthIn(max = 84.dp)) {
                Latency(server.delayMillis, selected, compact = true)
            }
        }
    } else {
        Column(card.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(Modifier.fillMaxWidth().height(23.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Flag(server.country, supporting)
                    ServerSelectionMark(selected, foreground)
                }
                Spacer(Modifier.height(6.dp))
                ServerSummary(server, foreground, supporting)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                ServerFavorite(server.favorite, supporting, favorite)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Latency(server.delayMillis, selected) }
            }
        }
    }
}

@Composable
private fun ServerSummary(server: FlowServer, titleColor: Color, subtitleColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(server.name, color = titleColor, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(server.protocolLabel(), color = subtitleColor, fontSize = 11.sp, lineHeight = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ServerSelectionMark(selected: Boolean, foreground: Color) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        selected,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = .62f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = .62f),
    ) { FlowIcon(R.drawable.flow_selected, 16.dp, foreground) }
}

@Composable
private fun ServerFavorite(marked: Boolean, ink: Color, change: () -> Unit, compact: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(if (marked) R.string.flow_favorite_remove else R.string.flow_favorite_add)
    Box(
        Modifier.size(if (compact) 24.dp else 28.dp, if (compact) 20.dp else 30.dp)
            .clip(RoundedCornerShape(9.dp)).flowPress(interaction)
            .semantics { contentDescription = label }
            .toggleable(marked, interaction, ripple(), role = Role.Checkbox) { change() },
        contentAlignment = if (compact) Alignment.CenterEnd else Alignment.Center,
    ) {
        Crossfade(marked, modifier = Modifier.size(20.dp),
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "favorite star") { favorite ->
            FlowIcon(if (favorite) R.drawable.flow_star_filled else R.drawable.flow_star_outline, 20.dp, ink)
        }
    }
}


@Composable
internal fun SettingsPage(
    modifier: Modifier,
    bottomInset: Dp,
    compact: Boolean,
    systemColors: Boolean,
    autoConnect: Boolean,
    detectCountry: Boolean,
    subscriptionHwid: Boolean,
    dynamicIsland: Boolean,
    automaticDividers: Boolean,
    actions: FlowActivity,
    show: (FlowSettingsPage) -> Unit,
) {
    val language = SettingsManager.getLocale()
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().flowHeaderFade(99.dp, 30.dp, visible = listState.canScrollBackward),
            state = listState,
            contentPadding = PaddingValues(top = 99.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsGroup(R.string.flow_connection, R.drawable.flow_shield) {
                    Setting(R.string.flow_autostart, R.string.flow_autostart_sub, checked = autoConnect, toggle = actions::changeAutoConnect)
                    Setting(R.string.flow_split, R.string.flow_split_sub, click = { show(FlowSettingsPage.ROUTING) })
                    Setting(R.string.flow_always, R.string.flow_always_sub, click = actions::openAndroidVpnSettings)
                }
            }
            item {
                SettingsGroup(R.string.flow_interface, R.drawable.flow_android) {
                    ExpandableSetting(R.string.flow_theme, R.string.flow_theme_sub,
                        R.string.flow_theme_flow, R.string.flow_theme_system, systemColors,
                        enabled = Build.VERSION.SDK_INT >= 31, select = actions::changeTheme)
                    ExpandableSetting(R.string.flow_dividers, R.string.flow_dividers_sub,
                        R.string.flow_dividers_servers, R.string.flow_dividers_auto, automaticDividers,
                        select = actions::changeAutomaticDividers)
                    Setting(R.string.flow_language, R.string.flow_language_sub, language.getDisplayLanguage(language),
                        click = { show(FlowSettingsPage.LANGUAGE) })
                    Setting(R.string.flow_dynamic_island, R.string.flow_dynamic_island_sub,
                        checked = dynamicIsland, toggle = actions::changeDynamicIsland)
                }
            }
            item {
                SettingsGroup(R.string.flow_diagnostics, R.drawable.flow_diagnostics) {
                    Setting(R.string.flow_logs, R.string.flow_logs_sub, click = { show(FlowSettingsPage.LOGS) })
                    Setting(R.string.flow_about, R.string.flow_about_sub, click = { show(FlowSettingsPage.ABOUT) })
                }
            }
            item {
                SettingsGroup(R.string.flow_advanced, R.drawable.flow_shield) {
                    Setting(R.string.flow_island, R.string.flow_island_sub, click = actions::openNotificationSettings)
                    Setting(R.string.flow_lookup, R.string.flow_lookup_sub, checked = detectCountry, toggle = actions::changeCountryDetection)
                    Setting(R.string.flow_hwid, R.string.flow_hwid_sub, checked = subscriptionHwid, toggle = actions::changeSubscriptionHwid)
                    Setting(R.string.flow_advanced, R.string.flow_advanced_sub, click = { show(FlowSettingsPage.ADVANCED) })
                }
            }
        }
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            PageTitle(stringResource(R.string.flow_settings), compact, stringResource(R.string.flow_platform))
        }
    }
}

@Composable
private fun ExpandableSetting(
    label: Int, subtitle: Int, first: Int, second: Int, checked: Boolean,
    enabled: Boolean = true, select: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Setting(label, subtitle, stringResource(if (checked) second else first),
            enabled = enabled, click = { expanded = !expanded }, dropdown = true, expanded = expanded)
        AnimatedVisibility(
            expanded,
            enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Choice(stringResource(first), !checked) { select(false) }
                Choice(stringResource(second), checked) { select(true) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(label: Int, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowIcon(icon, 19.dp, colors.secondary)
            Text(stringResource(label), color = colors.secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun Setting(
    label: Int,
    subtitle: Int,
    value: String? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    toggle: ((Boolean) -> Unit)? = null,
    click: (() -> Unit)? = null,
    dropdown: Boolean = false,
    expanded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val control = when {
        checked != null && toggle != null -> Modifier.toggleable(
            value = checked, interactionSource = interaction, indication = ripple(),
            role = Role.Switch, enabled = enabled, onValueChange = toggle,
        )
        click != null -> Modifier.clickable(
            interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = click,
        )
        else -> Modifier
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).flowPress(interaction).clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainer).then(control)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(label), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), fontSize = 14.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        }
        if (checked != null && toggle != null) {
            Switch(checked, onCheckedChange = null, enabled = enabled, thumbContent = {
                AnimatedVisibility(checked, enter = scaleIn(motion.fastSpatialSpec(), initialScale = .6f),
                    exit = scaleOut(motion.fastSpatialSpec(), targetScale = .6f)) {
                    FlowIcon(R.drawable.flow_status_on, 12.dp, colors.primary)
                }
            })
        } else {
            if (value != null) {
                AnimatedContent(
                    value, transitionSpec = {
                        fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
                    }, label = "setting value", modifier = Modifier.widthIn(max = 124.dp),
                ) { Text(it, fontSize = 13.sp, color = colors.secondary, fontWeight = FontWeight.Medium) }
            }
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.fastSpatialSpec(),
                label = "setting chevron")
            Box(Modifier.graphicsLayer { rotationZ = rotation }) {
                FlowIcon(if (dropdown) R.drawable.flow_expand else R.drawable.flow_settings_arrow, 18.dp, colors.onSurfaceVariant)
            }
        }
    }
}


