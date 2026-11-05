package org.jarsi.devicewatch.overlay

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.QuickMetric.BATTERY
import org.jarsi.devicewatch.data.QuickMetric.BATTERY_POWER
import org.jarsi.devicewatch.data.QuickMetric.BATTERY_TEMP
import org.jarsi.devicewatch.data.QuickMetric.CPU_LOAD
import org.jarsi.devicewatch.data.QuickMetric.MEMORY
import org.junit.Test

/** The floating card's decisions, free of Android: selection cap, place, gestures, tile state. */
class FloatingFigureLogicTest {

    @Test
    fun `a fourth figure is refused`() {
        val three = setOf(BATTERY, BATTERY_TEMP, CPU_LOAD)
        assertThat(limitOverlaySelection(three, MEMORY, true)).isEqualTo(three)
        assertThat(limitOverlaySelection(three, CPU_LOAD, false)).containsExactly(BATTERY, BATTERY_TEMP)
        assertThat(limitOverlaySelection(setOf(BATTERY, BATTERY_TEMP), CPU_LOAD, true)).isEqualTo(three)
    }

    @Test
    fun `the last figure cannot be switched off`() {
        // Review: an empty stored selection made Settings and the card disagree.
        assertThat(limitOverlaySelection(setOf(MEMORY), MEMORY, false)).containsExactly(MEMORY)
        assertThat(limitOverlaySelection(setOf(MEMORY, BATTERY), MEMORY, false)).containsExactly(BATTERY)
    }

    @Test
    fun `a card never dragged in this orientation keeps the other orientation's place`() {
        // Review: the card jumped to the default corner on the first rotation after a drag.
        val portrait = OverlayPosition(0f, 1f)
        val landscape = OverlayPosition(0.5f, 0.5f)
        assertThat(overlayPositionChoice(landscape = true, portrait = portrait, landscapePosition = null)).isEqualTo(portrait)
        assertThat(overlayPositionChoice(landscape = true, portrait = portrait, landscapePosition = landscape)).isEqualTo(landscape)
        assertThat(overlayPositionChoice(landscape = false, portrait = null, landscapePosition = landscape)).isEqualTo(landscape)
        assertThat(overlayPositionChoice(landscape = false, portrait = null, landscapePosition = null)).isNull()
    }

    @Test
    fun `nothing chosen means the default pair`() {
        assertThat(overlayMetricsOrDefault(emptySet())).containsExactly(BATTERY_TEMP, BATTERY_POWER)
        assertThat(overlayMetricsOrDefault(setOf(MEMORY))).containsExactly(MEMORY)
    }

    @Test
    fun `three figures stack in portrait only`() {
        assertThat(overlayLayout(3, landscape = false)).isEqualTo(OverlayLayout.COLUMN)
        assertThat(overlayLayout(3, landscape = true)).isEqualTo(OverlayLayout.ROW)
        assertThat(overlayLayout(2, landscape = false)).isEqualTo(OverlayLayout.ROW)
        assertThat(overlayLayout(1, landscape = false)).isEqualTo(OverlayLayout.ROW)
    }

    @Test
    fun `pixels clamp at both edges`() {
        assertThat(overlayPixels(OverlayPosition(1f, 1f), 300, 100, 1080, 2400)).isEqualTo(780 to 2300)
        assertThat(overlayPixels(OverlayPosition(0f, 0f), 300, 100, 1080, 2400)).isEqualTo(0 to 0)
        // No stored place yet: the top-right corner.
        assertThat(overlayPixels(null, 300, 100, 1080, 2400)).isEqualTo(780 to 0)
        // A card wider than the screen sits at the left edge rather than off it.
        assertThat(overlayPixels(OverlayPosition(1f, 0.5f), 1200, 100, 1080, 2400)).isEqualTo(0 to 1150)
    }

    @Test
    fun `fraction round trips and keeps the card on a rotated screen`() {
        val fraction = overlayFraction(780, 1150, 300, 100, 1080, 2400)
        assertThat(fraction.xFraction).isWithin(0.001f).of(1f)
        assertThat(fraction.yFraction).isWithin(0.001f).of(0.5f)
        assertThat(overlayPixels(fraction, 300, 100, 2400, 1080)).isEqualTo(2100 to 490)
        // A card wider than the screen has no room to measure a share in.
        assertThat(overlayFraction(0, 0, 1200, 100, 1080, 2400).xFraction).isEqualTo(0f)
    }

    @Test
    fun `the card is shown only when on, allowed and the screen is on`() {
        assertThat(overlayAction(shown = true, permission = true, screenOn = true)).isEqualTo(OverlayAction.SHOW)
        assertThat(overlayAction(shown = true, permission = true, screenOn = false)).isEqualTo(OverlayAction.HIDE)
        assertThat(overlayAction(shown = true, permission = false, screenOn = true)).isEqualTo(OverlayAction.TURN_OFF)
        assertThat(overlayAction(shown = true, permission = false, screenOn = false)).isEqualTo(OverlayAction.TURN_OFF)
        assertThat(overlayAction(shown = false, permission = true, screenOn = true)).isEqualTo(OverlayAction.HIDE)
        assertThat(overlayAction(shown = false, permission = false, screenOn = true)).isEqualTo(OverlayAction.HIDE)
    }

    @Test
    fun `the tile is active only with the card up and allowed`() {
        assertThat(overlayTileLook(permission = true, shown = true)).isEqualTo(OverlayTileLook(active = true, needsPermission = false))
        assertThat(overlayTileLook(permission = false, shown = true)).isEqualTo(OverlayTileLook(active = false, needsPermission = true))
        assertThat(overlayTileLook(permission = true, shown = false)).isEqualTo(OverlayTileLook(active = false, needsPermission = false))
    }

    @Test
    fun `a short touch is a tap even with a little movement`() {
        val gesture = OverlayGesture(slopPx = 8f, longPressMs = 500L)
        gesture.down(0f, 0f, atMs = 0L)
        assertThat(gesture.up(atMs = 100L)).isEqualTo(OverlayTap.TAP)

        gesture.down(0f, 0f, atMs = 0L)
        assertThat(gesture.move(2f, 2f, atMs = 50L)).isNull()
        assertThat(gesture.up(atMs = 100L)).isEqualTo(OverlayTap.TAP)
    }

    @Test
    fun `movement past the slop is a drag and never a tap or a long press`() {
        val gesture = OverlayGesture(slopPx = 8f, longPressMs = 500L)
        gesture.down(10f, 10f, atMs = 0L)
        assertThat(gesture.move(40f, 10f, atMs = 50L)).isEqualTo(30f to 0f)
        assertThat(gesture.move(50f, 15f, atMs = 60L)).isEqualTo(40f to 5f)
        assertThat(gesture.up(atMs = 100L)).isEqualTo(OverlayTap.NONE)

        gesture.down(10f, 10f, atMs = 0L)
        assertThat(gesture.move(40f, 10f, atMs = 50L)).isEqualTo(30f to 0f)
        assertThat(gesture.up(atMs = 900L)).isEqualTo(OverlayTap.NONE)
    }

    @Test
    fun `holding still long enough is a long press`() {
        val gesture = OverlayGesture(slopPx = 8f, longPressMs = 500L)
        gesture.down(0f, 0f, atMs = 0L)
        assertThat(gesture.up(atMs = 600L)).isEqualTo(OverlayTap.LONG_PRESS)
        assertThat(gesture.longPressDue(atMs = 499L)).isFalse()
        gesture.down(0f, 0f, atMs = 0L)
        assertThat(gesture.longPressDue(atMs = 500L)).isTrue()
    }

    @Test
    fun `constants`() {
        assertThat(OVERLAY_MAX_FIGURES).isEqualTo(3)
        assertThat(OVERLAY_DEFAULT_POSITION).isEqualTo(OverlayPosition(1f, 0f))
        assertThat(OVERLAY_BUTTONS_VISIBLE_MS).isEqualTo(4_000L)
        assertThat(OVERLAY_LONG_PRESS_MS).isEqualTo(500L)
    }
}
