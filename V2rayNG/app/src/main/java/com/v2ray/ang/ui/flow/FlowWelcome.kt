package com.v2ray.ang.ui.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

@Composable
internal fun FlowWelcome(continueHome: () -> Unit, setup: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entered = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        val viewport = maxHeight
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = 26.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    entered,
                    enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.slowSpatialSpec(), initialScale = .69f),
                ) {
                    Box(
                        Modifier.size(156.dp).clip(RoundedCornerShape(46.dp))
                            .background(colors.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(R.drawable.flow_logo),
                            contentDescription = null,
                            modifier = Modifier.size(124.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    entered,
                    enter = fadeIn(motion.slowEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 3 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.flow_welcome_title),
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.flow_welcome_description),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            AnimatedVisibility(
                entered,
                enter = fadeIn(motion.slowEffectsSpec()) + slideInVertically(motion.slowSpatialSpec()) { it / 2 },
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = setup,
                        shapes = ButtonDefaults.shapes(RoundedCornerShape(25.dp), RoundedCornerShape(15.dp)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    ) {
                        Text(stringResource(R.string.flow_welcome_setup), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    FilledTonalButton(
                        onClick = continueHome,
                        shapes = ButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(13.dp)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.flow_welcome_continue), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
