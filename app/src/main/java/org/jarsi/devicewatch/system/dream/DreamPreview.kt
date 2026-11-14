package org.jarsi.devicewatch.system.dream

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The phone the preview pretends to be: an upright 360 x 780 dp screen. */
private val PREVIEW_SCREEN_WIDTH = 360.dp
private val PREVIEW_SCREEN_HEIGHT = 780.dp

/**
 * The fixed sample the settings preview draws, with the real date and minute. It is
 * refreshed when the minute changes; the seconds stand still.
 */
@Composable
internal fun rememberSampleDreamUiState(): DreamUiState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val is24Hour = remember(configuration) { android.text.format.DateFormat.is24HourFormat(context) }
    val minute by produceState(initialValue = System.currentTimeMillis() / 60_000L) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis() / 60_000L
        }
    }
    return remember(context, minute, locale, is24Hour) {
        sampleDreamUiState(context, nowMs = minute * 60_000L, locale = locale, is24Hour = is24Hour)
    }
}

/**
 * A thumbnail of one style, drawn by the screensaver's own code. The style is laid out for
 * an upright 360 x 780 dp phone whatever the thumbnail's size: the density is scaled down
 * rather than the picture, so the text stays sharp. Nothing moves and nothing pulses, and
 * the thumbnail says nothing to a screen reader: the picker around it names the style.
 */
@Composable
internal fun DreamPreview(
    style: DreamStyle,
    options: DreamOptions,
    modifier: Modifier = Modifier,
    state: DreamUiState = rememberSampleDreamUiState(),
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(PREVIEW_SCREEN_WIDTH / PREVIEW_SCREEN_HEIGHT)
            .clearAndSetSemantics { }
    ) {
        val density = LocalDensity.current
        val scale = maxWidth / PREVIEW_SCREEN_WIDTH
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            DreamScreen(
                state = state,
                options = options.copy(style = style, layoutSwapped = false),
                landscape = false,
                onTap = null,
                moving = false,
            )
        }
    }
}
