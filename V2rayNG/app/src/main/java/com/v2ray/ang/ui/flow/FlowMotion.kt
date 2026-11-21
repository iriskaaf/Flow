package com.v2ray.ang.ui.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun FlowToggleContent(
    checked: Boolean, modifier: Modifier = Modifier, label: String, content: @Composable (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = checked,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it * direction / 2 })
                .togetherWith(fadeOut(motion.fastEffectsSpec()) +
                    slideOutVertically(motion.fastSpatialSpec()) { -it * direction / 2 })
        },
        label = label,
    ) { content(it) }
}

@Composable
internal fun Modifier.flowPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .965f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "control press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

internal fun Modifier.flowHeaderFade(height: Dp, feather: Dp, visible: Boolean = true): Modifier {
    if (!visible) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val end = height.toPx().coerceAtLeast(1f)
        val mask = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startY = (end - feather.toPx()).coerceAtLeast(0f), endY = end,
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, size = Size(size.width, end.coerceAtMost(size.height)), blendMode = BlendMode.DstIn)
        }
    }
}

@Composable
internal fun flowChoiceRadius(selected: Boolean, idle: Dp, chosen: Dp): Dp =
    animateDpAsState(
        if (selected) chosen else idle, MaterialTheme.motionScheme.fastSpatialSpec(), label = "choice shape",
    ).value.coerceAtLeast(0.dp)
