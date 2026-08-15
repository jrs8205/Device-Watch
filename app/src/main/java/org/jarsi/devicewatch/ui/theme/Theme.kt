package org.jarsi.devicewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * Material You is deliberately not used. A wallpaper-derived scheme picks the
 * colours at runtime, which means no contrast ratio in this app could be stated
 * — let alone held to AAA — because the pairs would differ on every device.
 */
@Composable
fun ModernWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
