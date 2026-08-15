package org.jarsi.devicewatch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's fixed palette, held to WCAG AAA by `AppPaletteTest`: text clears 7:1
 * and meaningful graphics 3:1 against every surface they land on — the page, the
 * navigation bar and bottom sheet, and dialogs.
 *
 * From the "Mittaristo" design handoff, with two corrections the measurements
 * forced. The dark accent is split in two because one value cannot do both jobs:
 * [DarkAccentGraphic] is bright enough to read as a meter fill but too bright to
 * carry AAA text (nothing can — black on it reaches only 6.7:1), so text, links
 * and the selection pill use [DarkAccent] instead.
 */
val DarkBackground = Color(0xFF0A0A0C)

/** Navigation bar and bottom sheet. */
val DarkSurfaceContainer = Color(0xFF17171B)

/** Dialogs, one step further up. */
val DarkSurfaceContainerHigh = Color(0xFF1E1E24)

/** Switch tracks and icon backgrounds. */
val DarkSurfaceVariant = Color(0xFF17171B)
val DarkOnSurface = Color(0xFFF2F2F5)

/** Raised from the handoff's #A2A2AE, which fell to 6.57:1 on a dialog. */
val DarkOnSurfaceVariant = Color(0xFFA8A8B4)
val DarkAccent = Color(0xFF90CAF9)

/** Meter fills only — never text. */
val DarkAccentGraphic = Color(0xFF2196F3)
val DarkOnAccent = Color(0xFF04283F)
val DarkAccentContainer = Color(0xFF123047)
val DarkOnAccentContainer = Color(0xFFC8E4FB)
val DarkOutline = Color(0xFF6A6A75)

/** Meter track: deliberately faint, it carries no information of its own. */
val DarkOutlineVariant = Color(0xFF2A2A32)
val DarkStatusOk = Color(0xFF66BB6A)
val DarkStatusWarn = Color(0xFFEDA100)
val DarkError = Color(0xFFF2B8B5)

/** One accent does both jobs here: dark enough for text, dark enough for meters. */
val LightBackground = Color(0xFFFAFAFC)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEFF4)
val LightOnSurface = Color(0xFF101014)
val LightOnSurfaceVariant = Color(0xFF4E4E58)
val LightAccent = Color(0xFF0A4C87)
val LightOnAccent = Color(0xFFFFFFFF)
val LightAccentContainer = Color(0xFFD6E7F7)
val LightOnAccentContainer = Color(0xFF06263D)
val LightOutline = Color(0xFF8E8E99)
val LightOutlineVariant = Color(0xFFDADAE2)
val LightStatusOk = Color(0xFF145A1E)
val LightStatusWarn = Color(0xFF664200)
val LightError = Color(0xFF8C1D24)

/** How strongly a status colour tints the notice band behind it. */
const val STATUS_TINT_ALPHA = 0.12f
