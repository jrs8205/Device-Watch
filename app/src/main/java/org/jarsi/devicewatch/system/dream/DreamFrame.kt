package org.jarsi.devicewatch.system.dream

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jarsi.devicewatch.system.burnInOffset

/**
 * Whether the screensaver is really running. The settings preview draws the same styles
 * standing still: no burn-in shift and no pulses.
 */
internal val LocalDreamMoving = staticCompositionLocalOf { true }

/**
 * What every style shares: the 180° flip for a phone docked upside down, the once-a-minute
 * burn-in shift and the tap that closes the screensaver. A style draws into two places.
 * [background] stays put: it is neither shifted nor rotated, so a style that tints the
 * screen mirrors itself when [swapped]. [content] is rotated by the flip and moves with
 * the shift; it gets the whole screen's constraints.
 */
@Composable
internal fun DreamFrame(
    swapped: Boolean,
    verticalShiftOnly: Boolean,
    moving: Boolean,
    onTap: (() -> Unit)?,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    // OLED burn-in protection: once a minute the whole content moves to a new spot (see
    // burnInOffset). GitHub #5: the move used to be a single jump that people noticed from the
    // corner of their eye; it now glides there over a few seconds.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val glideX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = BURN_IN_GLIDE_MS, easing = LinearOutSlowInEasing),
        label = "burnInGlideX",
    )
    val glideY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = tween(durationMillis = BURN_IN_GLIDE_MS, easing = LinearOutSlowInEasing),
        label = "burnInGlideY",
    )
    // Read in the layout phase through the lambda overload, so a glide frame only re-lays out.
    val glideOffset: Density.() -> IntOffset = { IntOffset(glideX.dp.roundToPx(), glideY.dp.roundToPx()) }

    if (moving) {
        LaunchedEffect(verticalShiftOnly) {
            while (true) {
                kotlinx.coroutines.delay(60000)
                val next = burnInOffset(Math.random(), Math.random(), verticalShiftOnly)
                offsetX = next.xDp
                offsetY = next.yDp
            }
        }
    }

    CompositionLocalProvider(LocalDreamMoving provides moving) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val screen = this
            background()

            // Main container; rotation handles the 180-degree layout flip dynamically.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(glideOffset)
                    .graphicsLayer {
                        rotationZ = if (swapped) 180f else 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                screen.content()
            }
        }
    }
}

/**
 * The screensaver's whole picture for one state: the frame and the chosen style inside it.
 * The running screensaver and the settings preview both draw through this.
 */
@Composable
internal fun DreamScreen(
    state: DreamUiState,
    options: DreamOptions,
    landscape: Boolean,
    onTap: (() -> Unit)?,
    moving: Boolean = true,
) {
    DreamFrame(
        swapped = options.layoutSwapped,
        verticalShiftOnly = options.verticalShiftOnly,
        moving = moving,
        onTap = onTap,
        background = {
            when (options.style) {
                DreamStyle.GLOW -> GlowBackground(level = state.batteryLevel, swapped = options.layoutSwapped)
            }
        },
        content = {
            when (options.style) {
                DreamStyle.GLOW -> GlowContent(state = state, options = options, landscape = landscape)
            }
        },
    )
}
