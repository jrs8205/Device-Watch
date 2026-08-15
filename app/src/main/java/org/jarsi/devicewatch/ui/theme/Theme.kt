package org.jarsi.devicewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Every role is set, including the ones this app never names directly: any left
 * at its Material default would fall back to the baseline purple and quietly
 * escape the palette — the navigation bar's own container and selection pill
 * did exactly that.
 *
 * `secondaryContainer` is the accent at full strength rather than a faint tint,
 * because it marks selection (tab, chip, segmented button) and a tint at 1.5:1
 * cannot carry a state.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBackground,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkOnSurface,
    inversePrimary = DarkAccentContainer,
    secondary = DarkAccent,
    onSecondary = DarkBackground,
    secondaryContainer = DarkAccent,
    onSecondaryContainer = DarkBackground,
    tertiary = DarkAccent,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkAccentContainer,
    onTertiaryContainer = DarkOnSurface,
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
    surfaceBright = DarkSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainer,
    surfaceContainerHighest = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkBackground,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkError,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightBackground,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnSurface,
    inversePrimary = LightAccentContainer,
    secondary = LightAccent,
    onSecondary = LightBackground,
    secondaryContainer = LightAccent,
    onSecondaryContainer = LightBackground,
    tertiary = LightAccent,
    onTertiary = LightBackground,
    tertiaryContainer = LightAccentContainer,
    onTertiaryContainer = LightOnSurface,
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
    surfaceBright = LightBackground,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainer,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightError,
    onError = LightBackground,
    errorContainer = LightErrorContainer,
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
