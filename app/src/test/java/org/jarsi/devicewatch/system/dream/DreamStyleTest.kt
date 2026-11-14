package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The screensaver styles' stored ids and the table of what each style can show. */
class DreamStyleTest {

    @Test
    fun `an unknown or missing style id means Glow`() {
        for (id in listOf(null, "", "edge2", "GLOW")) {
            assertWithMessage("id $id").that(dreamStyleOf(id)).isEqualTo(DreamStyle.GLOW)
        }
    }

    @Test
    fun `every style finds itself by its id`() {
        for (style in DreamStyle.entries) {
            assertThat(dreamStyleOf(style.id)).isEqualTo(style)
        }
        assertThat(DreamStyle.entries.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `Glow shows everything but the calendar`() {
        assertThat(shownBy(DreamStyle.GLOW))
            .containsExactlyElementsIn(DreamField.entries - DreamField.CALENDAR)
    }

    @Test
    fun `Minimal shows the date, the notification icons and the battery details only`() {
        assertThat(shownBy(DreamStyle.MINIMAL)).containsExactly(
            DreamField.DATE,
            DreamField.NOTIFICATIONS,
            DreamField.BATTERY_DETAILS,
        )
    }

    @Test
    fun `Night shows the alarm only`() {
        assertThat(shownBy(DreamStyle.NIGHT)).containsExactly(DreamField.ALARM)
    }

    @Test
    fun `Ring shows seconds, date, notification icons and battery details`() {
        assertThat(shownBy(DreamStyle.RING)).containsExactly(
            DreamField.SECONDS,
            DreamField.DATE,
            DreamField.NOTIFICATIONS,
            DreamField.BATTERY_DETAILS,
        )
    }

    private fun shownBy(style: DreamStyle): List<DreamField> =
        DreamField.entries.filter { style.supports(it) }
}
