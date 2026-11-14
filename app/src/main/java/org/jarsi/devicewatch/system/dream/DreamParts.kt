package org.jarsi.devicewatch.system.dream

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.system.NotificationIconGroup
import org.jarsi.devicewatch.system.NotificationRowFit
import org.jarsi.devicewatch.system.fitNotificationRow

// The pieces the screensaver's styles share: the clock block, the notification row, the
// text fitting and the battery's colours.

/** A style's outer paddings and the gap between its two landscape columns. */
internal data class DreamInsets(val horizontal: Dp, val vertical: Dp, val centerGap: Dp)

/** The paddings every style uses, chosen by the screen's size. */
internal fun dreamInsets(width: Dp, height: Dp): DreamInsets = DreamInsets(
    horizontal = when {
        width < 380.dp -> 20.dp
        width < 520.dp -> 28.dp
        else -> 40.dp
    },
    vertical = if (height < 560.dp) 24.dp else 40.dp,
    centerGap = if (width < 700.dp) 18.dp else 32.dp,
)

/**
 * Test tag for a text that is meant to end in an ellipsis when it is too long (a calendar
 * event's title). Every other text on the screensaver must fit; the fit test skips these.
 */
internal const val DREAM_TRUNCATES_TAG = "dream-truncates"

@Composable
internal fun ClockBlock(
    modifier: Modifier = Modifier,
    timeText: String,
    secondsText: String,
    dateText: String,
    clockSize: Int,
    secondsSize: Int,
    dateSize: Int,
    amPmText: String = "",
    alarmText: String = "",
    deviceName: String? = null,
    notificationGroups: List<NotificationIconGroup> = emptyList(),
    notificationPulseToken: Int = 0,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (deviceName != null) {
            Text(
                text = deviceName,
                autoSize = fitUpTo((dateSize * 0.72f).sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B929C),
                letterSpacing = 1.5.sp,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeText,
                fontSize = clockSize.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFEDEDED),
                style = TextStyle(fontFeatureSettings = "tnum"),
                letterSpacing = 0.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = ":$secondsText",
                    autoSize = fitUpTo(secondsSize.sp),
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                if (amPmText.isNotEmpty()) {
                    Text(
                        text = amPmText,
                        autoSize = fitUpTo((secondsSize * 0.6f).sp),
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B929C)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateText,
            autoSize = fitUpTo(dateSize.sp),
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B929C)
        )
        if (alarmText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = alarmText,
                autoSize = fitUpTo((dateSize * 0.72f).sp),
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6E7681),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        if (notificationGroups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            NotificationIconRow(
                groups = notificationGroups,
                pulseToken = notificationPulseToken,
                iconSize = dateSize.dp,
            )
        }
    }
}

/** How long the burn-in shift takes to glide to its new spot. */
internal const val BURN_IN_GLIDE_MS = 4000

/** How many apps the notification row shows before it folds the rest into "+N". */
private const val MAX_NOTIFICATION_ICONS = 8

/** The row's alpha between pulses: present, but quieter than the clock. */
private const val NOTIFICATION_ROW_RESTING_ALPHA = 0.6f

/**
 * One small icon per app with unread notifications, tinted like the status bar, with a count
 * when the app has several. A new arrival breathes the row three times (dim → bright → dim)
 * before it settles back; a row that was already showing when the dream started stays quiet.
 */
@Composable
internal fun NotificationIconRow(
    groups: List<NotificationIconGroup>,
    pulseToken: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val rowAlpha = remember { Animatable(NOTIFICATION_ROW_RESTING_ALPHA) }
    LaunchedEffect(pulseToken) {
        if (pulseToken == 0) return@LaunchedEffect
        repeat(3) {
            rowAlpha.animateTo(1f, tween(durationMillis = 600))
            rowAlpha.animateTo(0.3f, tween(durationMillis = 600))
        }
        rowAlpha.animateTo(NOTIFICATION_ROW_RESTING_ALPHA, tween(durationMillis = 400))
    }
    val total = groups.sumOf { it.count }
    val description = pluralStringResource(R.plurals.dream_unread_notifications, total, total)
    val countSize = (iconSize.value * 0.6f).sp
    val countTextStyle = TextStyle(
        fontSize = countSize,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val measurer = rememberTextMeasurer()
    val resolver = LocalFontFamilyResolver.current
    // Review round 10: eight groups with two-digit counts overflow a 344 dp clock block, so
    // the row keeps only the groups whose measured widths fit, "+N" included.
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        val fit = remember(groups, maxWidth, iconSizePx, countTextStyle, density, resolver) {
            fun labelWidth(text: String): Float = measurer.measure(
                text = text,
                style = countTextStyle,
                softWrap = false,
                maxLines = 1,
                density = density,
                fontFamilyResolver = resolver,
            ).size.width.toFloat()
            fitNotificationRow(
                groups = groups,
                availableWidth = with(density) { maxWidth.toPx() },
                iconWidth = iconSizePx.toFloat(),
                spacing = with(density) { NOTIFICATION_ROW_SPACING.toPx() },
                countGap = with(density) { NOTIFICATION_COUNT_GAP.toPx() },
                countWidth = { count -> labelWidth(count.toString()) },
                overflowWidth = { hidden -> labelWidth("+$hidden") },
                maxGroups = MAX_NOTIFICATION_ICONS,
            )
        }
        NotificationIconRowContent(
            fit = fit,
            iconSize = iconSize,
            iconSizePx = iconSizePx,
            countStyle = countTextStyle,
            modifier = Modifier
                .graphicsLayer { alpha = rowAlpha.value }
                .semantics { contentDescription = description },
        )
    }
}

/** Gap between the groups on the notification row, and between an icon and its count. */
private val NOTIFICATION_ROW_SPACING = 10.dp
private val NOTIFICATION_COUNT_GAP = 3.dp

@Composable
private fun NotificationIconRowContent(
    fit: NotificationRowFit,
    iconSize: Dp,
    iconSizePx: Int,
    countStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NOTIFICATION_ROW_SPACING),
    ) {
        fit.shown.forEach { group ->
            key(group.packageName) {
                // Review round 10: a URI-backed icon reads a content provider, so the decode
                // runs off the main thread and the icon appears when it is ready.
                val bitmap by produceState<ImageBitmap?>(null, group.newestPostTime, iconSizePx) {
                    value = withContext(Dispatchers.IO) {
                        loadNotificationIcon(context, group.smallIcon, iconSizePx)
                    }
                }
                val ready = bitmap
                if (ready != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = ready,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            colorFilter = ColorFilter.tint(Color(0xFFEDEDED)),
                        )
                        if (group.count > 1) {
                            Spacer(modifier = Modifier.width(NOTIFICATION_COUNT_GAP))
                            Text(
                                text = group.count.toString(),
                                maxLines = 1,
                                softWrap = false,
                                color = Color(0xFF8B929C),
                                style = countStyle,
                            )
                        }
                    }
                }
            }
        }
        if (fit.hidden > 0) {
            Text(
                text = "+${fit.hidden}",
                maxLines = 1,
                softWrap = false,
                color = Color(0xFF8B929C),
                style = countStyle,
            )
        }
    }
}

/**
 * Renders a notification's small icon at [sizePx]. Icons of other packages resolve through
 * their own resources, which can fail for an app that was just uninstalled or updated; then
 * nothing is drawn for that app rather than a broken placeholder. Called off the main
 * thread: a URI icon opens a content provider.
 */
private fun loadNotificationIcon(context: Context, icon: android.graphics.drawable.Icon?, sizePx: Int): ImageBitmap? {
    icon ?: return null
    if (sizePx <= 0) return null
    return try {
        icon.loadDrawable(context)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Largest clock font size (in sp) whose "HH:mm" + gap + ":ss" line fits [availableWidth].
 * Text width scales linearly with font size, so we measure both parts once at a reference size
 * and let [fittedClockSp] solve for the biggest size that fits, clamped to
 * [minClockSp]..[maxClockSp]. This keeps the seconds from wrapping/overlapping on narrow
 * screens regardless of device width. The measurement is cached and only re-runs when the
 * available width, density, or font resolver changes (not on every clock tick).
 *
 * A style whose clock has no seconds beside it passes a [secondsRatio] and [secondsMinSp]
 * of zero and no [gap]; [timeWeight] and [fontFamily] are the weight and the face the
 * style draws the time in.
 */
@Composable
internal fun rememberFittedClockSize(
    availableWidth: Dp,
    maxClockSp: Int,
    minClockSp: Int,
    secondsRatio: Float = 0.25f,
    secondsMinSp: Int = 22,
    gap: Dp = 8.dp,
    timeWeight: FontWeight = FontWeight.ExtraBold,
    fontFamily: FontFamily? = null,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    return remember(
        availableWidth, maxClockSp, minClockSp, secondsRatio, secondsMinSp, gap, timeWeight, fontFamily,
        density, resolver,
    ) {
        if (availableWidth <= 0.dp) return@remember minClockSp
        val referenceSp = 100f
        val timeWidthPx = measurer.measure(
            text = "00:00",
            style = TextStyle(
                fontSize = referenceSp.sp,
                fontWeight = timeWeight,
                fontFamily = fontFamily,
                fontFeatureSettings = "tnum",
                letterSpacing = 0.sp,
            ),
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width.toFloat()
        val secWidthPx = measurer.measure(
            text = ":00",
            style = TextStyle(
                fontSize = referenceSp.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                fontFeatureSettings = "tnum",
            ),
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width.toFloat()
        fittedClockSp(
            availablePx = with(density) { availableWidth.toPx() },
            gapPx = with(density) { gap.toPx() },
            timePerSp = timeWidthPx / referenceSp,
            secPerSp = secWidthPx / referenceSp,
            secondsRatio = secondsRatio,
            secondsMinSp = secondsMinSp,
            minClockSp = minClockSp,
            maxClockSp = maxClockSp,
        )
    }
}

/**
 * Pure width-fit math, extracted for unit testing. Given the time/seconds widths-per-sp,
 * returns the largest integer sp size in [minClockSp]..[maxClockSp] whose
 * time + [gapPx] + seconds line fits [availablePx]. The seconds normally scale as
 * [secondsRatio] of the clock size, but are pinned at [secondsMinSp] once that would drop
 * below the floor — so the floor case is re-solved with a fixed seconds width.
 */
internal fun fittedClockSp(
    availablePx: Float,
    gapPx: Float,
    timePerSp: Float,
    secPerSp: Float,
    secondsRatio: Float,
    secondsMinSp: Int,
    minClockSp: Int,
    maxClockSp: Int,
): Int {
    var fitted = (availablePx - gapPx) / (timePerSp + secPerSp * secondsRatio)
    if (secondsRatio * fitted < secondsMinSp) {
        val secFlooredPx = secPerSp * secondsMinSp
        fitted = (availablePx - gapPx - secFlooredPx) / timePerSp
    }
    return fitted.toInt().coerceIn(minClockSp, maxClockSp)
}

/**
 * Screensaver lines stay on one line: they shrink from [max] toward a floor
 * when a long localized date or a wide details line would otherwise wrap. A line that is
 * still too wide at the usual floor on the narrowest screens gets a lower [floorSp].
 */
internal fun fitUpTo(max: TextUnit, floorSp: Float = 12f): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = minOf(floorSp, max.value).sp, maxFontSize = max, stepSize = 1.sp)

internal fun getBatteryGradientColors(level: Int): List<Color> {
    return when {
        level <= 20 -> listOf(Color(0xFFEF4444), Color(0xFFF87171))
        level <= 60 -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> listOf(Color(0xFF34D399), Color(0xFF6EE7A8))
    }
}

/**
 * Deep background tint matching the battery-bar color thresholds. Used as the bottom stop
 * of the screensaver's background gradient (black at the top). Values are bright enough to
 * read as a clear color glow on an OLED panel while staying comfortably dark.
 */
internal fun batteryTintDeep(level: Int): Color {
    return when {
        level <= 20 -> Color(0xFF4A1210)
        level <= 60 -> Color(0xFF46360A)
        else -> Color(0xFF0E4A36)
    }
}
