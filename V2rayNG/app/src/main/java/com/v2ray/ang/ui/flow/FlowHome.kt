package com.v2ray.ang.ui.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun HomePage(
    modifier: Modifier,
    bottomInset: Dp,
    profiles: FlowProfiles,
    running: Boolean,
    pending: Boolean,
    testing: Boolean,
    systemColors: Boolean,
    compact: Boolean,
    short: Boolean,
    openServers: () -> Unit,
    select: (String) -> Unit,
    details: (FlowServer) -> Unit,
    toggle: () -> Unit,
    test: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val favorites = profiles.favorites
    var dockPixels by remember { mutableIntStateOf(0) }
    val origin = remember { arrayOf(Offset.Zero) }
    val destination = remember { arrayOfNulls<Rect>(1) }
    var flight by remember { mutableStateOf<ServerFlight?>(null) }
    var returnSource by remember { mutableStateOf<Pair<FlowServer, Rect>?>(null) }
    var returnFlight by remember { mutableStateOf<ServerFlight?>(null) }
    LaunchedEffect(returnSource) {
        // A favorite below the viewport has no measured row to land on.
        if (returnSource != null) {
            delay(150)
            returnSource = null
        }
    }
    val dockHeight = if (dockPixels == 0) 188.dp else with(density) { dockPixels.toDp() }
    val rowHeight = if (compact) 56.dp else 60.dp

    Column(modifier) {
        PageTitle("Flow", compact, height = if (short) 60.dp else 80.dp, leading = {
            FlowIcon(R.drawable.flow_notification, if (short) 38.dp else 48.dp, colors.primary)
        })
        Spacer(Modifier.height(if (short) 3.dp else 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { origin[0] = it.positionInRoot() }) {
            val listBottom = bottomInset + dockHeight + 8.dp
            // Keep a short favorites list close to the active connection panel.
            val spaceAbove = if (favorites.isNotEmpty() && density.fontScale <= 1.15f) {
                (maxHeight - listBottom - 55.dp - (rowHeight + 1.dp) * favorites.size).coerceAtLeast(0.dp)
            } else 0.dp

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottom),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                item(key = "favorites-heading") {
                    Column(Modifier.padding(top = spaceAbove, bottom = 7.dp)) {
                        FavoritesHeading(compact, openServers)
                    }
                }
                if (favorites.isEmpty()) {
                    item(key = "no-favorites") {
                        EmptyMessage(
                            when {
                                profiles.servers.isEmpty() -> R.string.flow_no_servers
                                profiles.active?.favorite == true -> R.string.flow_no_other_favorites
                                else -> R.string.flow_no_favorites
                            },
                            openServers,
                            modifier = Modifier.animateItem(placementSpec = motion.defaultSpatialSpec()),
                        )
                    }
                }
                itemsIndexed(favorites, key = { _, server -> server.id }) { index, server ->
                    val source = remember { arrayOfNulls<Rect>(1) }
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 24.dp else 8.dp,
                        topEnd = if (index == 0) 24.dp else 8.dp,
                        bottomStart = if (index == favorites.lastIndex) 24.dp else 8.dp,
                        bottomEnd = if (index == favorites.lastIndex) 24.dp else 8.dp,
                    )
                    ServerLine(
                        server, false, compact, shape,
                        {
                            if (!pending && profiles.selected != server.id) {
                                val from = source[0]
                                val to = destination[0]
                                if (from != null && to != null && from.height > 0f && to.height > 0f) {
                                    flight = ServerFlight(server, from.top - origin[0].y, to.translate(-origin[0]))
                                    profiles.active?.takeIf { it.favorite }?.let { previous ->
                                        returnSource = previous to to.translate(-origin[0])
                                    }
                                }
                                select(server.id)
                            }
                        },
                        { details(server) },
                        modifier = Modifier.animateItem(
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeInSpec = if (returnSource?.first?.id == server.id || returnFlight?.server?.id == server.id) null
                                else motion.fastEffectsSpec(),
                            fadeOutSpec = if (flight?.server?.id == server.id) null else motion.fastEffectsSpec(),
                        )
                            .onGloballyPositioned {
                                val bounds = it.boundsInRoot()
                                source[0] = bounds
                                val returning = returnSource
                                if (returning?.first?.id == server.id && bounds.height > 0f) {
                                    returnFlight = ServerFlight(server, returning.second.top, bounds.translate(-origin[0]), false)
                                    returnSource = null
                                }
                            }
                            .graphicsLayer {
                                alpha = if (returnFlight?.server?.id == server.id || returnSource?.first?.id == server.id) 0f else 1f
                            },
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset)
                    .onSizeChanged { dockPixels = it.height }
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, .18f to colors.surface.copy(alpha = .96f)))
                    .padding(top = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.flow_active), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ConnectionStatus(running, pending, systemColors)
                }
                Spacer(Modifier.height(6.dp))
                val active = profiles.active
                AnimatedContent(
                    targetState = active,
                    modifier = Modifier.onGloballyPositioned { destination[0] = it.boundsInRoot() },
                    contentKey = { it?.id },
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it / 4 })
                            .togetherWith(fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 4 })
                    },
                    label = "active profile",
                ) { server ->
                    if (server == null) {
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = rowHeight).flowPress(interaction)
                                .clip(RoundedCornerShape(21.dp)).background(colors.surfaceContainer)
                                .clickable(interaction, ripple(), onClick = openServers).padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.flow_no_selection), color = colors.onSurfaceVariant, fontSize = 14.sp)
                        }
                    } else {
                        ServerLine(server, true, compact, RoundedCornerShape(21.dp), { details(server) }, { details(server) },
                            Modifier.graphicsLayer {
                                alpha = if (flight?.server?.id == server.id || returnFlight?.server?.id == server.id ||
                                    returnSource?.first?.id == server.id) 0f else 1f
                            })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionSlider(Modifier.weight(1f), running, pending, short, toggle)
                    ActionSquare(
                        if (running) R.drawable.flow_speed_on else R.drawable.flow_speed_dark,
                        R.string.flow_ping_home, if (short) 59.dp else 72.dp,
                        colors.secondaryContainer, colors.onSecondaryContainer, test, profiles.homePingIds.isNotEmpty() && !pending,
                        loading = testing,
                    )
                }
            }
            flight?.let { transfer -> ServerTransfer(transfer, compact) { if (flight == transfer) flight = null } }
            returnFlight?.let { transfer ->
                ServerTransfer(transfer, compact) { if (returnFlight == transfer) returnFlight = null }
            }
        }
    }
}

private data class ServerFlight(val server: FlowServer, val sourceTop: Float, val to: Rect, val toActive: Boolean = true)

@Composable
private fun ServerTransfer(flight: ServerFlight, compact: Boolean, finish: () -> Unit) {
    val density = LocalDensity.current
    val progress = remember(flight) { Animatable(0f) }
    LaunchedEffect(flight) {
        progress.animateTo(1f, spring(
            dampingRatio = .62f, stiffness = Spring.StiffnessLow, visibilityThreshold = .002f,
        ))
        finish()
    }
    Box(
        Modifier.absoluteOffset {
            val t = progress.value.coerceAtLeast(0f)
            val distance = flight.to.top - flight.sourceTop
            val overshoot = (distance * (t - 1f).coerceAtLeast(0f)).coerceIn(-10.dp.toPx(), 10.dp.toPx())
            IntOffset(
                flight.to.left.roundToInt(),
                (flight.sourceTop + distance * t.coerceAtMost(1f) + overshoot).roundToInt(),
            )
        }.width(with(density) { flight.to.width.toDp() }).clearAndSetSemantics {},
    ) {
        val blend = progress.value.coerceIn(0f, 1f)
        ServerLine(flight.server, true, compact, RoundedCornerShape(21.dp), {}, {},
            transferProgress = if (flight.toActive) blend else 1f - blend)
    }
}

@Composable
private fun FavoritesHeading(compact: Boolean, openServers: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.flow_favorites)
    val textMeasurer = rememberTextMeasurer()
    val minSize = 18.sp
    val titleStyle = TextStyle(color = colors.onSurface, fontWeight = FontWeight.Medium, fontSize = minSize)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val canFitSingleLine = textMeasurer.measure(label, titleStyle, softWrap = false).size.width <= constraints.maxWidth
            if (canFitSingleLine) {
                BasicText(
                    label,
                    style = titleStyle,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minSize, if (compact) 22.sp else 28.sp, .5.sp),
                )
            } else {
                BasicText(label, style = titleStyle.copy(lineHeight = 24.sp))
            }
        }
        Button(
            onClick = openServers,
            shapes = ButtonDefaults.shapes(RoundedCornerShape(17.dp), RoundedCornerShape(10.dp)),
            modifier = Modifier.heightIn(min = 42.dp),
            colors = ButtonDefaults.buttonColors(colors.secondaryContainer, colors.onSecondaryContainer),
            contentPadding = PaddingValues(horizontal = if (compact) 9.dp else 12.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.flow_all_servers), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            FlowIcon(R.drawable.flow_arrow, 14.dp, colors.onSecondaryContainer)
        }
    }
}

@Composable
private fun ConnectionStatus(running: Boolean, pending: Boolean, systemColors: Boolean) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val connectedFill = if (systemColors) colors.tertiary else colors.tertiaryContainer
    val connectedInk = if (systemColors) colors.onTertiary else colors.onTertiaryContainer
    val background by animateColorAsState(
        if (running && !pending) connectedFill else colors.surfaceContainerHigh,
        motion.defaultEffectsSpec(), label = "connection status color",
    )
    val radius by animateDpAsState(if (running && !pending) 15.dp else 8.dp, motion.defaultSpatialSpec(),
        label = "connection status shape")
    val status = if (pending) R.string.flow_busy else if (running) R.string.flow_on else R.string.flow_off
    val label = stringResource(status)
    AnimatedContent(
        status,
        Modifier.clip(RoundedCornerShape(radius.coerceAtLeast(0.dp))).background(background)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            val direction = if (targetState == R.string.flow_off) -1 else 1
            (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it * direction / 2 } +
                scaleIn(motion.defaultSpatialSpec(), initialScale = .78f))
                .togetherWith(fadeOut(motion.fastEffectsSpec()) +
                    slideOutVertically(motion.fastSpatialSpec()) { -it * direction / 2 })
        },
        label = "connection status",
    ) { state ->
        val connected = state == R.string.flow_on
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                FlowIcon(if (connected) R.drawable.flow_status_on else R.drawable.flow_status_off,
                    if (connected) 12.dp else 6.dp, if (connected) connectedInk else colors.onSurfaceVariant)
            }
            Text(stringResource(state), color = if (connected) connectedInk else colors.onSurface,
                fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ConnectionSlider(modifier: Modifier, running: Boolean, pending: Boolean, short: Boolean, toggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val end = if (running) 1f else 0f
    var drag by remember { mutableFloatStateOf(end) }
    var dragging by remember { mutableStateOf(false) }
    val trigger by rememberUpdatedState(toggle)
    val handleWidth = if (short) 55.dp else 69.dp
    val sliderHeight = if (short) 59.dp else 72.dp
    val previewConnected = if (pending) !running else running
    val title = if (pending) R.string.flow_busy else if (previewConnected) R.string.flow_disconnect else R.string.flow_connect
    val wave = remember { Path() }
    val radius by animateDpAsState(
        if (pressed || dragging) 23.dp else 18.dp,
        motion.fastSpatialSpec(), label = "connection thumb shape",
    )
    val fraction by animateFloatAsState(
        if (dragging) drag else if (pending) 1f - end else end,
        animationSpec = if (dragging) snap() else motion.defaultSpatialSpec(),
        label = "connection thumb position",
    )
    fun position() = (if (dragging) drag else fraction).coerceIn(0f, 1f)

    BoxWithConstraints(modifier.heightIn(min = sliderHeight)) {
        val trackWidth = maxWidth
        val travel = (trackWidth - handleWidth - 16.dp).coerceAtLeast(0.dp)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val shape = RoundedCornerShape(27.dp)
            val actionLabel = stringResource(title)
            Box(
                Modifier.fillMaxWidth().heightIn(min = sliderHeight).flowPress(interaction)
                    .clip(shape).drawBehind {
                        drawRect(colors.secondary)
                        val progress = position()
                        if (progress > 0f) {
                            connectedRegion(wave, progress)
                            clipPath(wave) {
                                drawRect(colors.surfaceContainerHigh)
                                drawOutline(
                                    shape.createOutline(size, layoutDirection, this),
                                    colors.onSurface.copy(alpha = .82f),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                        }
                    }
                    .semantics { contentDescription = actionLabel }
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        enabled = !pending,
                        role = Role.Button,
                        onClickLabel = actionLabel,
                        onClick = toggle,
                    )
                    .pointerInput(running, pending, travel) {
                        if (pending || travel <= 0.dp) return@pointerInput
                        val distance = travel.toPx()
                        try {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var claimed = false
                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    val offset = change.position - down.position
                                    if (!claimed && change.pressed && offset.getDistance() > viewConfiguration.touchSlop) {
                                        claimed = true
                                        dragging = true
                                    }
                                    if (claimed) {
                                        drag = (end + offset.x / distance).coerceIn(0f, 1f)
                                        event.changes.forEach { it.consume() }
                                    }
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                }
                                dragging = false
                                if (claimed && released && abs(drag - end) >= .5f) trigger()
                            }
                        } finally {
                            dragging = false
                        }
                    },
            ) {
                for (connected in listOf(false, true)) {
                    val mask = remember { Path() }
                    var labelPixels by remember { mutableIntStateOf(0) }
                    val minEdge = 14.dp
                    val gap = 9.dp
                    val available = (trackWidth - handleWidth - 35.dp).coerceAtLeast(0.dp)
                    val foreground = if (connected) colors.onSurface else colors.onSecondary
                    Box(
                        Modifier.matchParentSize().clearAndSetSemantics {}.drawWithContent {
                            connectedRegion(mask, position())
                            clipPath(mask, if (connected) ClipOp.Intersect else ClipOp.Difference) { this@drawWithContent.drawContent() }
                        },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(
                            Modifier.absoluteOffset {
                                val thumb = (8.dp + travel * position()).toPx()
                                val x = if (connected) {
                                    (thumb - gap.toPx() - labelPixels).coerceAtLeast(minEdge.toPx())
                                } else {
                                    (thumb + (handleWidth + gap).toPx())
                                        .coerceAtMost((trackWidth - minEdge).toPx() - labelPixels)
                                }
                                IntOffset(x.roundToInt(), 0)
                            }.widthIn(max = available).onSizeChanged { labelPixels = it.width },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val text = if (pending) R.string.flow_busy
                            else if (connected) R.string.flow_disconnect else R.string.flow_connect
                            BasicText(stringResource(text),
                                style = TextStyle(color = foreground, fontWeight = FontWeight.SemiBold),
                                autoSize = TextAutoSize.StepBased(12.sp, 16.sp, .5.sp), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (!pending) {
                                Text(stringResource(if (connected) R.string.flow_left else R.string.flow_right),
                                    color = if (connected) colors.onSurfaceVariant else foreground, fontSize = 12.sp,
                                    lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterStart).offset {
                        IntOffset((8.dp + travel * position()).roundToPx(), 0)
                    }.size(handleWidth, sliderHeight - 16.dp).clip(RoundedCornerShape(radius))
                        .drawBehind { drawRect(lerp(colors.onSecondary, colors.surfaceContainerHighest, position())) },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = pending,
                        transitionSpec = {
                            (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = .68f))
                                .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = .68f))
                        },
                        label = "connection thumb icon",
                    ) { loading ->
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            if (loading) LoadingIndicator(color = colors.secondary, modifier = Modifier.size(38.dp))
                            else Box(Modifier.graphicsLayer { rotationZ = 180f * position() }) {
                                FlowIcon(R.drawable.flow_slide_connect, 25.dp, colors.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun DrawScope.connectedRegion(path: Path, progress: Float) {
    val front = size.width * progress
    val amplitude = 4.dp.toPx() * sin(progress * Math.PI).toFloat()
    path.rewind()
    path.moveTo(0f, 0f)
    path.lineTo(front, 0f)
    for (sample in 1..48) {
        val phase = sample / 48f
        val edge = front + amplitude * sin(phase * 12 * Math.PI).toFloat()
        path.lineTo(edge, size.height * phase)
    }
    path.lineTo(0f, size.height)
    path.close()
}
