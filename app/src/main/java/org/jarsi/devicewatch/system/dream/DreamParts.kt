package org.jarsi.devicewatch.system.dream

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.presentation.ui.valueTextStyle
import org.jarsi.devicewatch.system.NotificationIconGroup
import org.jarsi.devicewatch.system.NotificationRowFit
import org.jarsi.devicewatch.system.fitNotificationRow
import kotlin.math.roundToInt

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
    amPmLeads: Boolean = false,
    alarmText: String = "",
    deviceName: String? = null,
    notificationGroups: List<NotificationIconGroup> = emptyList(),
    notificationPulseToken: Int = 0,
    showSeconds: Boolean = true,
    showDate: Boolean = true,
    accent: Color = Color(0xFF38BDF8),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (deviceName != null) {
            val nameAlign = rememberWrapAlign(TextAlign.Center)
            Text(
                text = deviceName,
                // The user's own text: by its first letter, never by the screen's direction.
                style = valueTextStyle(),
                autoSize = fitUpTo((dateSize * 0.72f).sp),
                maxLines = DREAM_LINE_MAX_LINES,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = nameAlign.textAlign,
                onTextLayout = nameAlign.onTextLayout,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B929C),
                letterSpacing = 1.5.sp,
            )
        }
        LeftToRight {
            Row(verticalAlignment = Alignment.Bottom) {
                // Where the language reads AM or PM before the time, it stands on the time's left.
                val leadingMarker = amPmText.takeIf { amPmLeads }.orEmpty()
                val trailingMarker = amPmText.takeIf { !amPmLeads }.orEmpty()
                if (leadingMarker.isNotEmpty()) {
                    Text(
                        text = leadingMarker,
                        modifier = Modifier.padding(bottom = 16.dp),
                        fontSize = clockMarkerSp(secondsSize).sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B929C)
                    )
                    Spacer(modifier = Modifier.width(CLOCK_SIDE_GAP))
                }
                Text(
                    text = timeText,
                    fontSize = clockSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEDEDED),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    letterSpacing = 0.sp
                )
                // The column beside the time holds the seconds and, on a 12-hour clock whose
                // marker follows the time, AM/PM. With neither there is nothing to hold.
                if (showSeconds || trailingMarker.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(CLOCK_SIDE_GAP))
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        if (showSeconds) {
                            Text(
                                text = ":$secondsText",
                                autoSize = fitUpTo(secondsSize.sp),
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                        if (trailingMarker.isNotEmpty()) {
                            Text(
                                text = trailingMarker,
                                autoSize = fitUpTo(clockMarkerSp(secondsSize).sp),
                                maxLines = DREAM_LINE_MAX_LINES,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B929C)
                            )
                        }
                    }
                }
            }
        }
        if (showDate) {
            Spacer(modifier = Modifier.height(8.dp))
            val dateAlign = rememberWrapAlign(TextAlign.Center)
            Text(
                text = dateText,
                autoSize = fitUpTo(dateSize.sp),
                maxLines = DREAM_LINE_MAX_LINES,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = dateAlign.textAlign,
                onTextLayout = dateAlign.onTextLayout,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B929C)
            )
        }
        if (alarmText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            val alarmAlign = rememberWrapAlign(TextAlign.Center)
            Text(
                text = alarmText,
                autoSize = fitUpTo((dateSize * 0.72f).sp),
                maxLines = DREAM_LINE_MAX_LINES,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = alarmAlign.textAlign,
                onTextLayout = alarmAlign.onTextLayout,
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

/** The least room between the clock and the battery once the two have had to shrink. */
private val SHRUNK_BLOCK_GAP = 8.dp

/**
 * The upright layout Glow and Edge share: the clock block over the battery block, the room
 * left over split evenly above, between and below them.
 *
 * On a screen too low for both at their own size (320 x 480 dp with every extra switched on
 * and a large font) a plain column gave the last line, the charge estimate, the few pixels
 * that were left. Here both blocks are measured at the height they ask for, and when the two
 * do not fit they are drawn smaller by the same measure: the clock and the gaps give way and
 * every line keeps its whole height.
 */
@Composable
internal fun ClockOverBattery(
    modifier: Modifier = Modifier,
    clock: @Composable () -> Unit,
    battery: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { clock() }
            Box { battery() }
        },
    ) { measurables, constraints ->
        val asked = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val blocks = measurables.map { it.measure(asked) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val fit = fitBlocksInHeight(blocks.map { it.height }, height, SHRUNK_BLOCK_GAP.roundToPx())
        layout(width, height) {
            blocks.forEachIndexed { index, block ->
                val x = (width - block.width) / 2
                if (fit.scale == 1f) {
                    block.place(x, fit.tops[index])
                } else {
                    block.placeWithLayer(x, fit.tops[index]) {
                        scaleX = fit.scale
                        scaleY = fit.scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
        }
    }
}

/** Where each block of [ClockOverBattery] starts, and how much smaller they are all drawn. */
internal class BlockFit(val scale: Float, val tops: List<Int>)

/**
 * Blocks of [heights] stacked in [available] pixels. When they fit, the room left over is
 * split evenly above, between and below them. When they do not, they are all scaled down by
 * the same measure until they do, with [minGap] kept between them and none at the ends.
 */
internal fun fitBlocksInHeight(heights: List<Int>, available: Int, minGap: Int): BlockFit {
    val needed = heights.sum()
    if (needed <= available) {
        val gap = (available - needed) / (heights.size + 1f)
        var top = gap
        return BlockFit(1f, heights.map { height -> top.roundToInt().also { top += height + gap } })
    }
    val gaps = minGap * (heights.size - 1)
    val scale = ((available - gaps).coerceAtLeast(0)).toFloat() / needed
    var top = 0f
    return BlockFit(scale, heights.map { height -> top.roundToInt().also { top += height * scale + minGap } })
}

/** The room between the time and what stands beside it in [ClockBlock]. */
private val CLOCK_SIDE_GAP = 8.dp

/** The seconds beside [ClockBlock]'s time: a quarter of the time's size, and never below this. */
private const val CLOCK_SECONDS_RATIO = 0.25f
private const val CLOCK_SECONDS_MIN_SP = 22

/** AM or PM in [ClockBlock], by the size of the seconds. */
private fun clockMarkerSp(secondsSize: Int): Float = secondsSize * 0.6f

/** The size of the seconds beside a time of [clockSize]. */
internal fun clockSecondsSp(clockSize: Int): Int = (clockSize * CLOCK_SECONDS_RATIO).toInt().coerceAtLeast(CLOCK_SECONDS_MIN_SP)

/**
 * The largest size for [ClockBlock]'s time in [availableWidth], with what stands beside it:
 * the seconds or a marker that follows the time on its right, a marker that leads on its left.
 */
@Composable
internal fun rememberClockBlockSize(
    availableWidth: Dp,
    maxClockSp: Int,
    minClockSp: Int,
    showSeconds: Boolean,
    amPmText: String,
    amPmLeads: Boolean,
): Int {
    val leads = amPmLeads && amPmText.isNotEmpty()
    val hasSide = showSeconds || (amPmText.isNotEmpty() && !leads)
    return rememberFittedClockSize(
        availableWidth = availableWidth,
        maxClockSp = maxClockSp,
        minClockSp = minClockSp,
        secondsRatio = if (hasSide) CLOCK_SECONDS_RATIO else 0f,
        secondsMinSp = if (hasSide) CLOCK_SECONDS_MIN_SP else 0,
        gap = if (hasSide) CLOCK_SIDE_GAP else 0.dp,
        leadingText = if (leads) amPmText else "",
        leadingRatio = clockMarkerSp(1) * CLOCK_SECONDS_RATIO,
        leadingMinSp = clockMarkerSp(CLOCK_SECONDS_MIN_SP),
        leadingGap = CLOCK_SIDE_GAP,
        trailingText = if (leads) "" else amPmText,
        trailingSp = { clockSize -> clockMarkerSp(clockSecondsSp(clockSize)) },
    )
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
 *
 * [leadingText] is a marker drawn on the time's left (AM or PM where the language reads it
 * first), [leadingRatio] of the clock's size and never below [leadingMinSp], [leadingGap]
 * away from the time. The room it takes is taken off the width before the clock is sized.
 *
 * [trailingText] is a marker drawn on the time's right, in the seconds' column (AM or PM
 * where it follows the time), at the size [trailingSp] gives for a clock size and in
 * [trailingWeight]. Where it is wider than the seconds, the clock gives way to it.
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
    leadingText: String = "",
    leadingRatio: Float = 0f,
    leadingMinSp: Float = 0f,
    leadingGap: Dp = 0.dp,
    leadingWeight: FontWeight = FontWeight.Medium,
    trailingText: String = "",
    trailingSp: (clockSize: Int) -> Float = { 0f },
    trailingWeight: FontWeight = FontWeight.Medium,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    return remember(
        availableWidth, maxClockSp, minClockSp, secondsRatio, secondsMinSp, gap, timeWeight, fontFamily,
        leadingText, leadingRatio, leadingMinSp, leadingGap, leadingWeight, trailingText, trailingWeight, density, resolver,
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
        val leadWidthPx = if (leadingText.isEmpty()) {
            0f
        } else {
            measurer.measure(
                text = leadingText,
                style = TextStyle(fontSize = referenceSp.sp, fontWeight = leadingWeight, fontFamily = fontFamily),
                softWrap = false,
                maxLines = 1,
                density = density,
                fontFamilyResolver = resolver,
            ).size.width.toFloat()
        }
        // A text's width as it is drawn at a size, not worked out from the reference size: at
        // small sizes every letter's width is rounded to whole pixels, and over a long marker
        // that adds up to more than the line has to spare.
        fun drawnWidthPx(text: String, sizeSp: Float, weight: FontWeight, tabular: Boolean): Float = measurer.measure(
            text = text,
            style = TextStyle(
                fontSize = sizeSp.sp,
                fontWeight = weight,
                fontFamily = fontFamily,
                fontFeatureSettings = if (tabular) "tnum" else null,
            ),
            softWrap = false,
            maxLines = 1,
            density = density,
            fontFamilyResolver = resolver,
        ).size.width.toFloat()
        val gapPx = with(density) { gap.toPx() }
        val timePerSp = timeWidthPx / referenceSp
        val secPerSp = secWidthPx / referenceSp
        val availablePx = with(density) { availableWidth.toPx() }
        val leadGapPx = with(density) { leadingGap.toPx() }
        val reckoned = fittedClockSpBesideLead(
            availablePx = availablePx,
            leadPerSp = leadWidthPx / referenceSp,
            leadRatio = leadingRatio,
            leadMinSp = leadingMinSp,
            leadGapPx = leadGapPx,
        ) { roomPx ->
            val besideSeconds = fittedClockSp(
                availablePx = roomPx,
                gapPx = gapPx,
                timePerSp = timePerSp,
                secPerSp = secPerSp,
                secondsRatio = secondsRatio,
                secondsMinSp = secondsMinSp,
                minClockSp = minClockSp,
                maxClockSp = maxClockSp,
            )
            if (trailingText.isEmpty()) {
                besideSeconds
            } else {
                fittedClockSpBesideTrail(roomPx, besideSeconds, minClockSp) { clockSize ->
                    val secondsSp = maxOf(secondsRatio * clockSize, secondsMinSp.toFloat())
                    val secondsPx = if (secondsSp > 0f) drawnWidthPx(":00", secondsSp, FontWeight.Bold, tabular = true) else 0f
                    val markerPx = drawnWidthPx(trailingText, trailingSp(clockSize), trailingWeight, tabular = false)
                    if (markerPx <= secondsPx) {
                        // The marker stands inside the seconds' width: the line sized already.
                        timePerSp * clockSize + gapPx + secPerSp * secondsSp
                    } else {
                        drawnWidthPx("00:00", clockSize.toFloat(), timeWeight, tabular = true) + gapPx + markerPx
                    }
                }
            }
        }
        if (leadingText.isEmpty()) return@remember reckoned
        // A marker on the time's left was reckoned from its width at the reference size. Drawn
        // at ten or thirteen sp its letters are each a whole number of pixels wide, a little
        // more than reckoned, and on a narrow column the seconds at the line's other end lost
        // their last pixels. The whole line is measured as it is drawn.
        clockSpDrawnToFit(availablePx, reckoned, minClockSp) { clockSize ->
            val leadPx = drawnWidthPx(leadingText, maxOf(leadingRatio * clockSize, leadingMinSp), leadingWeight, tabular = false)
            val secondsSp = maxOf(secondsRatio * clockSize, secondsMinSp.toFloat())
            val secondsPx = if (secondsSp > 0f) drawnWidthPx(":00", secondsSp, FontWeight.Bold, tabular = true) else 0f
            leadPx + leadGapPx + drawnWidthPx("00:00", clockSize.toFloat(), timeWeight, tabular = true) + gapPx + secondsPx
        }
    }
}

/**
 * The clock's size when a marker stands on its right, in the seconds' column. [fitted] is
 * the size found for the time and its seconds alone; [lineWidthPx] is the width of the whole
 * line at a clock size, the column counted as wide as the wider of the seconds and the
 * marker. A marker no wider than the seconds changes nothing. A wider one (toki pona's word
 * for the afternoon is "pi pini suno", where English has "PM") ran off the screen's edge;
 * the clock steps down until the line fits, and never below [minClockSp].
 */
internal fun fittedClockSpBesideTrail(availablePx: Float, fitted: Int, minClockSp: Int, lineWidthPx: (Int) -> Float): Int =
    clockSpDrawnToFit(availablePx, fitted, minClockSp, lineWidthPx)

/**
 * [fitted] or the largest size under it at which the clock's line, as wide as [lineWidthPx]
 * says it is drawn, fits [availablePx]; never below [minClockSp].
 */
internal fun clockSpDrawnToFit(availablePx: Float, fitted: Int, minClockSp: Int, lineWidthPx: (Int) -> Float): Int {
    var size = fitted
    while (size > minClockSp && lineWidthPx(size) > availablePx) size--
    return size
}

/**
 * The clock's size when a marker stands on its left. [fit] sizes the clock for a width; the
 * marker is as wide as [leadPerSp] times its own size, which is [leadRatio] of the clock's
 * and never below [leadMinSp]. The clock is sized once without the marker, the marker's
 * width at that size and [leadGapPx] are taken off, and the clock is sized again: it only
 * gets smaller, and the marker with it, so the two fit.
 */
internal fun fittedClockSpBesideLead(
    availablePx: Float,
    leadPerSp: Float,
    leadRatio: Float,
    leadMinSp: Float,
    leadGapPx: Float,
    fit: (Float) -> Int,
): Int {
    val alone = fit(availablePx)
    if (leadPerSp <= 0f) return alone
    val leadPx = leadPerSp * maxOf(leadRatio * alone, leadMinSp) + leadGapPx
    return fit(availablePx - leadPx)
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
 * A clock's digits read from left to right in every language: the hours, the minutes, then
 * the seconds. In a language written from the right a plain row would put the seconds first.
 * AM or PM is another matter: its side is the language's own (see [amPmLeads]).
 */
@Composable
internal fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

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
