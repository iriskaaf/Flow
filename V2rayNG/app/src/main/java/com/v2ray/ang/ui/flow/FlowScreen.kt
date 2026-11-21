package com.v2ray.ang.ui.flow

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.v2ray.ang.R
import com.v2ray.ang.handler.FlowPreferences
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val FlowColors = darkColorScheme(
    background = Color(0xFF021225),
    onBackground = Color(0xFFF2FFF6),
    surface = Color(0xFF021225),
    surfaceContainerLow = Color(0xFF071F2D),
    surfaceContainer = Color(0xFF0B2635),
    surfaceContainerHigh = Color(0xFF103140),
    surfaceContainerHighest = Color(0xFF17424E),
    onSurface = Color(0xFFF2FFF6),
    inverseSurface = Color(0xFFF2FFF6),
    inverseOnSurface = Color(0xFF021225),
    onSurfaceVariant = Color(0xFFABCFCE),
    primary = Color(0xFF25C5E9),
    onPrimary = Color(0xFF021225),
    primaryContainer = Color(0xFF0D3B49),
    onPrimaryContainer = Color(0xFFCAFFDE),
    secondary = Color(0xFFCAFFDE),
    onSecondary = Color(0xFF021225),
    secondaryContainer = Color(0xFF248689),
    onSecondaryContainer = Color(0xFFCAFFE0),
    tertiaryContainer = Color(0xFF184B42),
    onTertiaryContainer = Color(0xFFCAFFDE),
    tertiary = Color(0xFFF4C57E),
    outline = Color(0xFF238689),
    error = Color(0xFFFF8793),
)

private enum class FlowPage(val label: Int, val outline: Int, val filled: Int) {
    SETTINGS(R.string.flow_settings, R.drawable.flow_symbol_settings, R.drawable.flow_symbol_settings_fill),
    HOME(R.string.flow_home, R.drawable.flow_symbol_home, R.drawable.flow_symbol_home_fill),
    SERVERS(R.string.flow_servers, R.drawable.flow_symbol_dns, R.drawable.flow_symbol_dns_fill),
}

@Composable
internal fun FlowScreen(
    profiles: FlowProfiles,
    running: Boolean,
    pending: Boolean,
    working: Boolean,
    systemColors: Boolean,
    autoConnect: Boolean,
    detectCountry: Boolean,
    subscriptionHwid: Boolean,
    welcomeCompleted: Boolean,
    pinging: Boolean,
    dynamicIsland: Boolean,
    actions: FlowActivity,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val darkTheme = isSystemInDarkTheme()
    val usesSystemColors = systemColors && Build.VERSION.SDK_INT >= 31
    val targetColors = remember(context, configuration, darkTheme, usesSystemColors) {
        if (usesSystemColors) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else FlowColors
    }
    val colors = animateFlowColors(targetColors)
    SideEffect {
        WindowCompat.getInsetsController(actions.window, actions.window.decorView).apply {
            isAppearanceLightStatusBars = colors.surface.luminance() > .5f
            isAppearanceLightNavigationBars = isAppearanceLightStatusBars
        }
    }
    val type = Typography()
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = type.copy(bodyLarge = type.bodyLarge.copy(letterSpacing = 0.sp)),
    ) {
        val pages = FlowPage.entries
        val pager = rememberPagerState(initialPage = FlowPage.HOME.ordinal) { pages.size }
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val motion = MaterialTheme.motionScheme
        var dialog by rememberSaveable { mutableStateOf<FlowDialog?>(null) }
        var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
        var subscriptionId by rememberSaveable { mutableStateOf<String?>(null) }
        var search by rememberSaveable { mutableStateOf("") }
        var sorted by rememberSaveable { mutableStateOf(false) }
        var settingsPath by rememberSaveable { mutableStateOf(arrayListOf<FlowSettingsPage>()) }
        val screenState = rememberSaveableStateHolder()
        val details = remember(profiles.allServers, detailsId) { profiles.allServers.firstOrNull { it.id == detailsId } }
        val subscription = remember(profiles.groups, subscriptionId) { profiles.groups.firstOrNull { it.id == subscriptionId } }

        LaunchedEffect(pager.settledPage) {
            if (pager.settledPage != FlowPage.SERVERS.ordinal) focusManager.clearFocus()
        }

        fun navigate(page: FlowPage) {
            scope.launch { pager.animateScrollToPage(page.ordinal, animationSpec = motion.defaultSpatialSpec()) }
        }
        fun showDetails(server: FlowServer) {
            detailsId = server.id
            dialog = FlowDialog.PROFILE
        }
        fun openSettings(page: FlowSettingsPage) {
            settingsPath = ArrayList(settingsPath + page)
        }
        fun closeSettings() {
            val page = settingsPath.lastOrNull() ?: return
            settingsPath = ArrayList(settingsPath.dropLast(1))
            screenState.removeState(page.name)
        }
        BackHandler(welcomeCompleted && settingsPath.isEmpty() && pager.currentPage != FlowPage.HOME.ordinal) {
            navigate(FlowPage.HOME)
        }
        BackHandler(settingsPath.isNotEmpty(), onBack = ::closeSettings)

        Surface(Modifier.fillMaxSize(), color = colors.surface, contentColor = colors.onSurface) {
            AnimatedContent(
                targetState = welcomeCompleted,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = .94f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.035f))
                },
                label = "welcome and main screen",
            ) { completed ->
                if (!completed) {
                    FlowWelcome(
                        continueHome = actions::completeWelcome,
                        setup = {
                            actions.completeWelcome()
                            scope.launch {
                                pager.scrollToPage(FlowPage.SERVERS.ordinal)
                                dialog = FlowDialog.ADD
                            }
                        },
                    )
                } else {
                    AnimatedContent(
                        targetState = settingsPath,
                        contentKey = { it.lastOrNull() },
                        transitionSpec = {
                            val direction = if (targetState.size > initialState.size) 1 else -1
                            (fadeIn(motion.fastEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it * direction / 8 })
                                .togetherWith(fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(motion.fastSpatialSpec()) { -it * direction / 12 })
                        },
                        label = "settings navigation",
                    ) { path ->
                        val destination = path.lastOrNull()
                        if (destination != null) {
                            screenState.SaveableStateProvider(destination.name) {
                                FlowSettingsDestination(destination, actions, ::closeSettings, ::openSettings)
                            }
                        } else screenState.SaveableStateProvider("tabs") {
                            BoxWithConstraints(
                                Modifier.fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                    .imePadding(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                val compact = maxWidth < 420.dp
                                val short = maxHeight < 660.dp
                                val edge = if (compact) 18.dp else 24.dp
                                val top = if (short) 4.dp else 17.dp
                                val navigationMargin = if (short) 5.dp else 10.dp
                                val navigationHeight = if (compact) 68.dp else 78.dp
                                val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val navigationInset = navigationHeight + navigationMargin + systemBottom + 12.dp

                                HorizontalPager(
                                    state = pager,
                                    modifier = Modifier.fillMaxSize().clipToBounds(),
                                    pageSize = PageSize.Fill,
                                    flingBehavior = PagerDefaults.flingBehavior(
                                        state = pager,
                                        snapAnimationSpec = motion.defaultSpatialSpec(),
                                        snapPositionalThreshold = .32f,
                                    ),
                                ) { index ->
                                    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.TopCenter) {
                                        val pageModifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
                                            .padding(start = edge, end = edge, top = top)
                                        when (pages[index]) {
                                            FlowPage.HOME -> HomePage(
                                                modifier = pageModifier,
                                                bottomInset = navigationInset,
                                                profiles = profiles,
                                                running = running,
                                                pending = pending,
                                                testing = pinging && actions.pingTarget == FlowActivity.HOME_PING,
                                                systemColors = usesSystemColors,
                                                compact = compact,
                                                short = short,
                                                openServers = { navigate(FlowPage.SERVERS) },
                                                select = { actions.chooseServer(it) },
                                                details = ::showDetails,
                                                toggle = if (!running && profiles.active == null) {
                                                    {
                                                        navigate(FlowPage.SERVERS)
                                                        if (profiles.servers.isEmpty()) dialog = FlowDialog.ADD
                                                    }
                                                } else actions::toggleVpn,
                                                test = actions::testHomeServers,
                                            )
                                            FlowPage.SERVERS -> ServersPage(
                                                modifier = pageModifier,
                                                bottomInset = navigationInset,
                                                profiles = profiles,
                                                search = search,
                                                searchChanged = { search = it },
                                                sorted = sorted,
                                                toggleSort = { sorted = !sorted },
                                                compact = compact,
                                                working = working,
                                                pinging = pinging,
                                                pingTarget = actions.pingTarget,
                                                add = { dialog = FlowDialog.ADD },
                                                select = { actions.chooseServer(it) },
                                                details = ::showDetails,
                                                favorite = actions::favorite,
                                                refresh = actions::refresh,
                                                subscriptionOptions = {
                                                    subscriptionId = it.id
                                                    dialog = FlowDialog.SUBSCRIPTION
                                                },
                                                ping = actions::testServers,
                                            )
                                            FlowPage.SETTINGS -> SettingsPage(
                                                modifier = pageModifier,
                                                bottomInset = navigationInset,
                                                compact = compact,
                                                systemColors = systemColors,
                                                autoConnect = autoConnect,
                                                detectCountry = detectCountry,
                                                subscriptionHwid = subscriptionHwid,
                                                dynamicIsland = dynamicIsland,
                                                automaticDividers = profiles.automaticDividers,
                                                actions = actions,
                                                show = ::openSettings,
                                            )
                                        }
                                    }
                                }
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .height(navigationInset)
                                        .background(Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            .40f to colors.surface.copy(alpha = .48f),
                                            1f to colors.surface,
                                        )),
                                )
                                FlowNavigation(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = systemBottom + navigationMargin),
                                    pager = pager,
                                    compact = compact,
                                    systemColors = usesSystemColors,
                                    navigate = ::navigate,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (welcomeCompleted) {
            FlowDialogs(
                dialog = dialog,
                details = details,
                subscription = subscription,
                groups = profiles.groups,
                actions = actions,
                show = { dialog = it },
            )
        }
    }
}

@Composable
private fun FlowNavigation(
    modifier: Modifier,
    pager: PagerState,
    compact: Boolean,
    systemColors: Boolean,
    navigate: (FlowPage) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { dragDistance += it }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier.clip(RoundedCornerShape(27.dp))
            .background(if (systemColors) colors.surfaceContainer else colors.primary)
            .draggable(
                drag,
                Orientation.Horizontal,
                onDragStarted = { dragDistance = 0f },
                onDragStopped = { velocity ->
                    val distance = if (isRtl) -dragDistance else dragDistance
                    if (abs(distance) >= threshold || (abs(distance) > threshold / 2 && abs(velocity) > threshold * 12)) {
                        val target = pager.currentPage + if (distance < 0) 1 else -1
                        FlowPage.entries.getOrNull(target)?.let(navigate)
                    }
                },
            )
            .padding(if (compact) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val side = if (compact) 52.dp else 58.dp
        FlowPage.entries.forEach { dest ->
            val current = dest.ordinal == pager.currentPage
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val radius by animateDpAsState(
                if (pressed) 12.dp else if (current) 23.dp else 16.dp,
                motion.fastSpatialSpec(),
                label = "tab shape",
            )
            val width by animateDpAsState(
                if (current) (if (compact) 131.dp else 148.dp) else side,
                motion.defaultSpatialSpec(), label = "tab width",
            )
            val label = stringResource(dest.label)
            val fill = when {
                !systemColors -> colors.inverseSurface
                current -> colors.primaryContainer
                else -> colors.surfaceContainerHighest
            }
            val ink = when {
                !systemColors -> colors.inverseOnSurface
                current -> colors.onPrimaryContainer
                else -> colors.onSurface
            }
            val animatedFill by animateColorAsState(fill, motion.fastEffectsSpec(), label = "tab fill")
            Row(
                Modifier.width(width.coerceAtLeast(side)).height(side)
                    .clip(RoundedCornerShape(radius))
                    .background(animatedFill)
                    .semantics { contentDescription = label; selected = current }
                    .clickable(interactionSource = interaction, indication = ripple(), role = Role.Tab) { navigate(dest) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelSpace = (width - side).coerceAtLeast(0.dp)
                val labelReveal = (labelSpace / 72.dp).coerceIn(0f, 1f)
                Box(Modifier.width(side - 8.dp * labelReveal).height(side), contentAlignment = Alignment.Center) {
                    Crossfade(current, animationSpec = motion.fastEffectsSpec(), label = "tab icon") { filled ->
                        FlowIcon(if (filled) dest.filled else dest.outline, 24.dp, ink)
                    }
                }
                Text(
                    label, Modifier.weight(1f).padding(end = 8.dp * labelReveal).clearAndSetSemantics {}
                        .graphicsLayer { alpha = labelReveal },
                    color = ink, fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
internal fun ServerLine(
    server: FlowServer,
    active: Boolean,
    compact: Boolean,
    shape: RoundedCornerShape,
    click: () -> Unit,
    details: () -> Unit,
    modifier: Modifier = Modifier,
    transferProgress: Float? = null,
) {
    val colors = MaterialTheme.colorScheme
    val progress = transferProgress ?: if (active) 1f else 0f
    val foreground = androidx.compose.ui.graphics.lerp(colors.onSurface, colors.onPrimary, progress)
    val supporting = androidx.compose.ui.graphics.lerp(colors.onSurfaceVariant, colors.onPrimary, progress)
    val interaction = remember { MutableInteractionSource() }
    val press = if (transferProgress != null) Modifier else Modifier.flowPress(interaction)
    val input = if (transferProgress != null) Modifier else Modifier.combinedClickable(
            interactionSource = interaction, indication = ripple(),
            onClick = click, onLongClick = details, onLongClickLabel = stringResource(R.string.flow_profile_options),
        )
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) 56.dp else 60.dp)
            .then(press).clip(shape)
            .background(androidx.compose.ui.graphics.lerp(colors.surfaceContainer, colors.primary, progress))
            .then(input)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp, 31.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center) { Flag(server.country, colors.onSurface) }
        Column(Modifier.weight(1f)) {
            Text(server.name, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(server.protocolLabel(),
                color = supporting, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Latency(server.delayMillis, active)
    }
}

@Composable
internal fun FlowServer.protocolLabel(): String = serverDescription ?: when (profile.configType) {
    com.v2ray.ang.enums.EConfigType.POLICYGROUP -> stringResource(R.string.flow_auto_server)
    com.v2ray.ang.enums.EConfigType.PROXYCHAIN -> stringResource(R.string.flow_proxy_chain)
    else -> protocol.ifBlank { stringResource(R.string.flow_protocol_unknown) }
}

@Composable
internal fun Latency(delay: Long, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = delay,
        modifier = Modifier.heightIn(min = 20.dp),
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
        },
        label = "latency result",
    ) { result ->
        val (color, dot) = when {
            active -> colors.onPrimary to R.drawable.flow_delay_selected
            result < 0 -> colors.onSurfaceVariant to R.drawable.flow_delay_none
            result >= 250 -> colors.error to R.drawable.flow_delay_bad
            result >= 100 -> colors.tertiary to R.drawable.flow_delay_medium
            else -> colors.secondary to R.drawable.flow_delay_good
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result != 0L) FlowIcon(dot, 8.dp, color)
            val label = when {
                result == 0L -> stringResource(R.string.flow_no_test)
                result < 0L -> stringResource(R.string.flow_no_response)
                else -> stringResource(R.string.flow_ms, result)
            }
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
internal fun Flag(country: String?, fallbackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val context = LocalContext.current
    val image = when (country) {
        "NL" -> R.drawable.flow_flag_nl
        "DE" -> R.drawable.flow_flag_de
        "FI" -> R.drawable.flow_flag_fi
        "PL" -> R.drawable.flow_flag_pl
        "SE" -> R.drawable.flow_flag_se
        else -> remember(country, context) { FlowPreferences.flagResource(context, country) }
    }
    if (image != null) {
        Image(painterResource(image), contentDescription = null,
            modifier = Modifier.size(30.dp, 20.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.FillBounds)
    } else {
        FlowIcon(R.drawable.flow_shield, 19.dp, fallbackColor)
    }
}

@Composable
internal fun FlowIcon(id: Int, size: Dp, tint: Color, label: String? = null) {
    Image(painterResource(id), contentDescription = label, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun ActionSquare(
    icon: Int,
    label: Int,
    size: Dp,
    background: Color,
    foreground: Color,
    click: () -> Unit,
    enabled: Boolean = true,
    rotation: Float = 0f,
    loading: Boolean = false,
    description: String? = null,
) {
    val title = description ?: stringResource(label)
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (loading) colors.secondaryContainer else background.copy(alpha = if (enabled) 1f else .45f),
        motion.fastEffectsSpec(), label = "action fill",
    )
    FilledIconButton(
        onClick = click,
        enabled = enabled && !loading,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container, contentColor = foreground,
            disabledContainerColor = container, disabledContentColor = foreground.copy(alpha = .58f),
        ),
        shapes = IconButtonDefaults.shapes(RoundedCornerShape(size * .33f), RoundedCornerShape(size * .18f)),
    ) {
        Box(Modifier.fillMaxSize().semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
            Crossfade(loading, modifier = Modifier.matchParentSize(),
                animationSpec = motion.fastEffectsSpec(), label = "action loading") { busy ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (busy) {
                        LoadingIndicator(modifier = Modifier.size(size * .62f), color = colors.onSecondaryContainer)
                    } else Crossfade(icon, modifier = Modifier.graphicsLayer { rotationZ = rotation },
                        animationSpec = motion.fastEffectsSpec(), label = "action icon") {
                        FlowIcon(it, if (size <= 48.dp) 20.dp else 24.dp, LocalContentColor.current)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageTitle(
    title: String, compact: Boolean, subtitle: String? = null, height: Dp = 80.dp,
    leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(title, style = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                autoSize = TextAutoSize.StepBased(24.sp, if (compact) 38.sp else 45.sp, 1.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun EmptyMessage(message: Int, click: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val container = modifier.fillMaxWidth().flowPress(interaction).clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
    Text(stringResource(message),
        (if (click == null) container else container.clickable(interaction, ripple(), onClick = click)).padding(17.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
}

@Composable
private fun animateFlowColors(target: ColorScheme): ColorScheme {
    val transition = updateTransition(target, label = "Flow palette")
    val spec = MotionScheme.expressive().defaultEffectsSpec<Color>()
    return target.copy(
        primary = transition.animateColor({ spec }) { it.primary }.value,
        onPrimary = transition.animateColor({ spec }) { it.onPrimary }.value,
        primaryContainer = transition.animateColor({ spec }) { it.primaryContainer }.value,
        onPrimaryContainer = transition.animateColor({ spec }) { it.onPrimaryContainer }.value,
        secondary = transition.animateColor({ spec }) { it.secondary }.value,
        onSecondary = transition.animateColor({ spec }) { it.onSecondary }.value,
        secondaryContainer = transition.animateColor({ spec }) { it.secondaryContainer }.value,
        onSecondaryContainer = transition.animateColor({ spec }) { it.onSecondaryContainer }.value,
        tertiary = transition.animateColor({ spec }) { it.tertiary }.value,
        tertiaryContainer = transition.animateColor({ spec }) { it.tertiaryContainer }.value,
        onTertiaryContainer = transition.animateColor({ spec }) { it.onTertiaryContainer }.value,
        surface = transition.animateColor({ spec }) { it.surface }.value,
        onSurface = transition.animateColor({ spec }) { it.onSurface }.value,
        onSurfaceVariant = transition.animateColor({ spec }) { it.onSurfaceVariant }.value,
        surfaceContainerLow = transition.animateColor({ spec }) { it.surfaceContainerLow }.value,
        surfaceContainer = transition.animateColor({ spec }) { it.surfaceContainer }.value,
        surfaceContainerHigh = transition.animateColor({ spec }) { it.surfaceContainerHigh }.value,
        surfaceContainerHighest = transition.animateColor({ spec }) { it.surfaceContainerHighest }.value,
        inverseSurface = transition.animateColor({ spec }) { it.inverseSurface }.value,
        inverseOnSurface = transition.animateColor({ spec }) { it.inverseOnSurface }.value,
        outline = transition.animateColor({ spec }) { it.outline }.value,
    )
}


