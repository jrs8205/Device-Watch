package org.jarsi.devicewatch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's fixed palette, held to WCAG AAA.
 *
 * Every text colour clears 7:1 and every meaningful graphic 3:1 against the
 * surfaces the app puts them on: the page background, the elevated surface
 * ([DarkSurfaceContainer]/[LightSurfaceContainer] — section cards, the
 * navigation bar), and the tinted notice cards. `AppPaletteTest` computes the
 * ratios, so a colour cannot be nudged without a failing test.
 *
 * The values come from the "Mittaristo" design handoff, with four raised for
 * surfaces the handoff did not have: it assumed a flat, card-less page, and on
 * the card its light outline, error and amber all fell short — and its
 * selection tint sat at 1.5:1, too weak to mark a selected tab.
 */
val DarkBackground = Color(0xFF0A0A0C)

/** Exactly `DarkSurfaceVariant` at 40 % over the background; the test pins it. */
val DarkSurfaceContainer = Color(0xFF17171B)
val DarkSurfaceVariant = Color(0xFF2A2A32)
val DarkOnSurface = Color(0xFFF2F2F5)
val DarkOnSurfaceVariant = Color(0xFFA2A2AE)

/** Text, icons and meters alike: one accent that clears 7:1 does every job. */
val DarkAccent = Color(0xFF90CAF9)
val DarkAccentContainer = Color(0xFF10304D)
val DarkOutline = Color(0xFF6A6A75)
val DarkStatusOk = Color(0xFF66BB6A)
val DarkStatusWarn = Color(0xFFEDA100)
val DarkError = Color(0xFFFFB4AB)
val DarkErrorContainer = Color(0xFF5C1A16)

val LightBackground = Color(0xFFFAFAFC)
val LightSurfaceContainer = Color(0xFFEDEDF2)
val LightSurfaceVariant = Color(0xFFDADAE2)
val LightOnSurface = Color(0xFF101014)
val LightOnSurfaceVariant = Color(0xFF4E4E58)
val LightAccent = Color(0xFF0A4C87)
val LightAccentContainer = Color(0xFFCFE4F7)
val LightOutline = Color(0xFF7C7C88)
val LightStatusOk = Color(0xFF145A1E)
val LightStatusWarn = Color(0xFF664200)
val LightError = Color(0xFF8C1D24)
val LightErrorContainer = Color(0xFFF6DEDC)

/** How strongly a status colour tints the notice card behind it. */
const val STATUS_TINT_ALPHA = 0.12f

/** The section card's fill over the page background. */
const val CARD_SURFACE_ALPHA = 0.4f
