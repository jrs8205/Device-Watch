package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/** The texts the screensaver's state carries: battery figures and the clock's lines. */
class DreamUiStateTest {

    private val finnish = Locale.forLanguageTag("fi-FI")

    /** Friday 13 November 2026 at 20:10:26, in the JVM's own time zone like the formats. */
    private val fridayEvening: Date = GregorianCalendar(2026, Calendar.NOVEMBER, 13, 20, 10, 26).time

    private fun formats(locale: Locale, is24Hour: Boolean) = DreamClockFormats(
        locale = locale,
        is24Hour = is24Hour,
        longDatePattern = if (locale == finnish) "EEEE d. MMMM" else "EEEE, MMMM d",
        shortDatePattern = if (locale == finnish) "EEE d. MMMM" else "EEE, MMMM d",
        compactDatePattern = if (locale == finnish) "EEE d.M." else "EEE MMM d",
    )

    private fun state(watts: String?) = DreamUiState(
        timeText = "20:10",
        secondsText = "26",
        amPmText = "",
        dateText = "Perjantaina 13. marraskuuta",
        shortDateText = "pe 13. marraskuuta",
        compactDateText = "pe 13.11.",
        alarmText = "",
        deviceName = null,
        batteryLevel = 64,
        chargeState = DreamChargeState.CHARGING,
        voltsText = "3,82 V",
        tempText = "26,3 °C",
        wattsText = watts,
        fullTimeText = "",
        fullClockText = "",
        notificationGroups = emptyList(),
        notificationPulseToken = 0,
        clockMillis = 0L,
    )

    @Test
    fun `battery figures use the locale's decimal separator`() {
        assertThat(batteryVoltsText(3.82, finnish)).isEqualTo("3,82 V")
        assertThat(batteryTempText(26.3, finnish)).isEqualTo("26,3 °C")
        assertThat(batteryWattsText(11.0, DreamChargeState.CHARGING, finnish)).isEqualTo("11,0 W")
        assertThat(batteryVoltsText(3.82, Locale.US)).isEqualTo("3.82 V")
        assertThat(batteryTempText(26.3, Locale.US)).isEqualTo("26.3 °C")
        assertThat(batteryWattsText(11.0, DreamChargeState.CHARGING, Locale.US)).isEqualTo("11.0 W")
    }

    @Test
    fun `watts show only while charging`() {
        for (state in listOf(DreamChargeState.HELD, DreamChargeState.FULL, DreamChargeState.ON_BATTERY)) {
            assertThat(batteryWattsText(11.0, state, finnish)).isNull()
        }
        assertThat(batteryWattsText(null, DreamChargeState.CHARGING, finnish)).isNull()
    }

    @Test
    fun `Glow's details line is unchanged`() {
        assertThat(glowDetailsText(state(watts = "11,0 W"))).isEqualTo("3,82 V  ·  26,3 °C  ·  11,0 W")
        assertThat(glowDetailsText(state(watts = null))).isEqualTo("3,82 V  ·  26,3 °C")
    }

    @Test
    fun `clock texts in English on a 12-hour clock`() {
        val texts = formats(Locale.US, is24Hour = false).texts(fridayEvening)

        assertThat(texts.time).isEqualTo("8:10")
        assertThat(texts.seconds).isEqualTo("26")
        assertThat(texts.amPm).isEqualTo("PM")
        assertThat(texts.date).isEqualTo("Friday, November 13")
        assertThat(texts.shortDate).isEqualTo("Fri, November 13")
        assertThat(texts.compactDate).isEqualTo("Fri Nov 13")
    }

    @Test
    fun `a 24-hour clock has no AM or PM`() {
        val texts = formats(Locale.US, is24Hour = true).texts(fridayEvening)

        assertThat(texts.time).isEqualTo("20:10")
        assertThat(texts.amPm).isEmpty()
    }

    @Test
    fun `the long date starts with a capital letter and the short ones stay as formatted`() {
        val texts = formats(finnish, is24Hour = true).texts(fridayEvening)

        assertThat(texts.date.first().isUpperCase()).isTrue()
        assertThat(texts.date.lowercase(finnish)).isEqualTo("perjantaina 13. marraskuuta")
        assertThat(texts.shortDate).isEqualTo("pe 13. marraskuuta")
        assertThat(texts.compactDate).isEqualTo("pe 13.11.")
    }

    @Test
    fun `the alarm reads weekday and time, and the estimate its time alone`() {
        val english = formats(Locale.US, is24Hour = false)
        val finnish24 = formats(finnish, is24Hour = true)

        assertThat(english.alarm(fridayEvening)).isEqualTo("Fri 8:10 PM")
        assertThat(finnish24.alarm(fridayEvening)).isEqualTo("pe 20:10")
        assertThat(english.clock(fridayEvening)).isEqualTo("8:10 PM")
        assertThat(finnish24.clock(fridayEvening)).isEqualTo("20:10")
    }
}
