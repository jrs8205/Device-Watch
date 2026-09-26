package org.jarsi.devicewatch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Every role is set, including the ones this app never names directly: any left
 * at its Material default falls back to the baseline purple and quietly escapes
 * the palette — the navigation bar's own container and selection pill did
 * exactly that.
 *
 * `secondaryContainer` is the accent at full strength rather than a faint tint,
 * because it marks selection (tab, chip, segmented button) and a tint at 1.5:1
 * cannot carry a state.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkOnAccentContainer,
    inversePrimary = DarkAccentContainer,
    secondary = DarkAccent,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkAccent,
    onSecondaryContainer = DarkOnAccent,
    tertiary = DarkAccent,
    onTertiary = DarkOnAccent,
    tertiaryContainer = DarkAccentContainer,
    onTertiaryContainer = DarkOnAccentContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkAccent,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = DarkBackground,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceContainerHigh,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkBackground,
    errorContainer = DarkAccentContainer,
    onErrorContainer = DarkError,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnAccentContainer,
    inversePrimary = LightAccentContainer,
    secondary = LightAccent,
    onSecondary = LightOnAccent,
    secondaryContainer = LightAccent,
    onSecondaryContainer = LightOnAccent,
    tertiary = LightAccent,
    onTertiary = LightOnAccent,
    tertiaryContainer = LightAccentContainer,
    onTertiaryContainer = LightOnAccentContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightAccent,
    inverseSurface = LightOnSurface,
    inverseOnSurface = LightBackground,
    surfaceDim = LightSurfaceVariant,
    surfaceBright = LightSurfaceContainer,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnAccent,
    errorContainer = LightAccentContainer,
    onErrorContainer = LightError,
)

/**
 * Material You is deliberately not the default. A wallpaper-derived scheme picks
 * the colours at runtime, which means no contrast ratio in this app could be
 * stated — let alone held to AAA — because the pairs would differ on every
 * device. It survives behind the [classicLook] switch for readers who prefer the
 * pre-1.6 look, together with the regular number font; on Android 11 and older,
 * where there is no dynamic colour, the classic look is the baseline purple
 * those versions shipped with.
 */
@Composable
fun ModernWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    classicLook: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        !classicLook -> if (darkTheme) DarkColorScheme else LightColorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ClassicDarkColorScheme
        else -> ClassicLightColorScheme
    }
    val metricFont = if (classicLook) FontFamily.Default else FontFamily.Monospace
    CompositionLocalProvider(LocalMetricFontFamily provides metricFont) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/**
 * The face every readout number is drawn in. Monospace by default, because the
 * values refresh every few seconds and proportional digits make columns jitter;
 * the classic look restores the regular face.
 */
val LocalMetricFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

private val ClassicDarkColorScheme = darkColorScheme(
    primary = ClassicPurple80,
    secondary = ClassicPurpleGrey80,
    tertiary = ClassicPink80
)

private val ClassicLightColorScheme = lightColorScheme(
    primary = ClassicPurple40,
    secondary = ClassicPurpleGrey40,
    tertiary = ClassicPink40
)
