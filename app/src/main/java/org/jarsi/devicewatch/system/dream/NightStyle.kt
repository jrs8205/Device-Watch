package org.jarsi.devicewatch.system.dream

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jarsi.devicewatch.R

// Night: pure black and nothing but dim red, for a bedroom. The clock, the next alarm and
// the battery level with a one-colour icon for the charger's state. No emoji: Android draws
// them in their own colours whatever the text colour is.

/** The clock: at least 3:1 on black, and no brighter than it needs to be. */
internal val NIGHT_CLOCK = Color(0xFFB3322A)

/** The small texts and icons: at least 4.5:1 on black. */
internal val NIGHT_TEXT = Color(0xFFD4483C)

@DrawableRes
internal fun nightChargeIcon(state: DreamChargeState): Int = when (state) {
    DreamChargeState.CHARGING, DreamChargeState.FULL -> R.drawable.ic_quick_power
    DreamChargeState.HELD -> R.drawable.ic_dream_plug
    DreamChargeState.ON_BATTERY -> R.drawable.ic_widget_battery
}

@Composable
internal fun BoxWithConstraintsScope.NightContent(state: DreamUiState, options: DreamOptions, landscape: Boolean) {
    val insets = dreamInsets(maxWidth, maxHeight)
    val locale = LocalConfiguration.current.locales[0]
    // The time alone, as in Minimal: no seconds and no AM/PM.
    val clockSize = rememberFittedClockSize(
        availableWidth = maxWidth - insets.horizontal * 2,
        maxClockSp = if (landscape) 96 else 132,
        minClockSp = if (landscape) 48 else 56,
        secondsRatio = 0f,
        secondsMinSp = 0,
        gap = 0.dp,
        timeWeight = FontWeight.Light,
    )
    // The same picture upright and on its side: everything on the centre line.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = insets.horizontal, vertical = insets.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = state.timeText,
            fontSize = clockSize.sp,
            fontWeight = FontWeight.Light,
            color = NIGHT_CLOCK,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFeatureSettings = "tnum"),
            letterSpacing = 0.sp
        )
        if (options.shows(DreamField.ALARM) && state.alarmText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            NightIconLine(
                iconRes = R.drawable.ic_dream_alarm,
                iconDescription = null,
                text = state.alarmText.uppercase(locale),
                textSize = 16.sp,
                letterSpacingEm = 0.2f,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        NightIconLine(
            iconRes = nightChargeIcon(state.chargeState),
            iconDescription = stringResource(state.chargeState.label),
            text = stringResource(R.string.dream_percent_spaced, state.batteryLevel),
            textSize = 18.sp,
            letterSpacingEm = 0.06f,
        )
        Spacer(modifier = Modifier.height(this@NightContent.maxHeight * 0.05f))
    }
}

/** A one-colour icon and a short text beside it, both in the small-text red. */
@Composable
private fun NightIconLine(
    @DrawableRes iconRes: Int,
    iconDescription: String?,
    text: String,
    textSize: TextUnit,
    letterSpacingEm: Float,
) {
    // The icon follows the text's size, the font scale included.
    val iconSize = with(LocalDensity.current) { textSize.toDp() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = iconDescription,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(NIGHT_TEXT),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            autoSize = fitUpTo(textSize),
            maxLines = 1,
            softWrap = false,
            color = NIGHT_TEXT,
            letterSpacing = letterSpacingEm.em,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
