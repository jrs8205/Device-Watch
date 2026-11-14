package org.jarsi.devicewatch.system.dream

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.NotificationIconGroup
import org.junit.Test

/** Which rows the Gauges style lists, in which order, and which give way when room runs out. */
class GaugeRowsTest {

    private val labels = GaugeLabels(
        battery = "AKKU",
        power = "TEHO",
        temperature = "LÄMPÖ",
        full = "TÄYNNÄ",
        alarm = "HERÄTYS",
        notifications = "ILMOITUKSET",
    )
    private val gauges = DreamOptions(style = DreamStyle.GAUGES)
    private val oneApp = listOf(NotificationIconGroup("app", 2, 1L, null))

    private fun kinds(rows: List<GaugeRow>) = rows.map { it.kind }

    @Test
    fun `charging shows every row in order`() {
        val rows = gaugeRows(
            state = plainDreamUiState(notificationGroups = oneApp),
            options = gauges,
            labels = labels,
            percent = "64 %",
        )

        assertThat(rows).containsExactly(
            GaugeRow(GaugeRowKind.BATTERY, "AKKU", "64 % ⚡"),
            GaugeRow(GaugeRowKind.POWER, "TEHO", "11,0 W"),
            GaugeRow(GaugeRowKind.TEMPERATURE, "LÄMPÖ", "26,3 °C"),
            GaugeRow(GaugeRowKind.FULL, "TÄYNNÄ", "~20:52"),
            GaugeRow(GaugeRowKind.ALARM, "HERÄTYS", "la 08:00"),
            GaugeRow(GaugeRowKind.NOTIFICATIONS, "ILMOITUKSET", ""),
        ).inOrder()
    }

    @Test
    fun `off the charger there is no power and no full row`() {
        val rows = gaugeRows(
            state = plainDreamUiState(
                chargeState = DreamChargeState.ON_BATTERY,
                wattsText = null,
                fullClockText = "",
                notificationGroups = oneApp,
            ),
            options = gauges,
            labels = labels,
            percent = "64 %",
        )

        assertThat(kinds(rows)).containsExactly(
            GaugeRowKind.BATTERY,
            GaugeRowKind.TEMPERATURE,
            GaugeRowKind.ALARM,
            GaugeRowKind.NOTIFICATIONS,
        ).inOrder()
        assertThat(rows.first().value).isEqualTo("64 % 🔋")
    }

    @Test
    fun `an empty alarm and an empty notification list leave their rows out`() {
        val rows = gaugeRows(
            state = plainDreamUiState(alarmText = "", notificationGroups = emptyList()),
            options = gauges,
            labels = labels,
            percent = "64 %",
        )

        assertThat(kinds(rows)).containsExactly(
            GaugeRowKind.BATTERY,
            GaugeRowKind.POWER,
            GaugeRowKind.TEMPERATURE,
            GaugeRowKind.FULL,
        ).inOrder()
    }

    @Test
    fun `a switched-off field leaves its row out`() {
        val rows = gaugeRows(
            state = plainDreamUiState(notificationGroups = oneApp),
            options = gauges.copy(showNotifications = false),
            labels = labels,
            percent = "64 %",
        )

        assertThat(kinds(rows)).doesNotContain(GaugeRowKind.NOTIFICATIONS)
        assertThat(kinds(rows)).contains(GaugeRowKind.ALARM)
    }

    private val eightRows = listOf(
        GaugeRow(GaugeRowKind.BATTERY, "AKKU", "64 % ⚡"),
        GaugeRow(GaugeRowKind.POWER, "TEHO", "11,0 W"),
        GaugeRow(GaugeRowKind.TEMPERATURE, "LÄMPÖ", "26,3 °C"),
        GaugeRow(GaugeRowKind.FULL, "TÄYNNÄ", "~20:52"),
        GaugeRow(GaugeRowKind.ALARM, "HERÄTYS", "la 08:00"),
        GaugeRow(GaugeRowKind.EVENT, "LA 10:00", "Hammaslääkäri"),
        GaugeRow(GaugeRowKind.EVENT, "SU", "Isänpäivälounas"),
        GaugeRow(GaugeRowKind.NOTIFICATIONS, "ILMOITUKSET", ""),
    )

    @Test
    fun `everything stays when there is room`() {
        assertThat(fitGaugeRows(eightRows, maxRows = 8)).isEqualTo(eightRows)
        assertThat(fitGaugeRows(eightRows, maxRows = 20)).isEqualTo(eightRows)
    }

    @Test
    fun `too many rows drop the events first, then notifications, alarm, full, temperature, power`() {
        val b = GaugeRowKind.BATTERY
        val p = GaugeRowKind.POWER
        val t = GaugeRowKind.TEMPERATURE
        val f = GaugeRowKind.FULL
        val a = GaugeRowKind.ALARM
        val e = GaugeRowKind.EVENT
        val n = GaugeRowKind.NOTIFICATIONS

        assertThat(kinds(fitGaugeRows(eightRows, 7))).containsExactly(b, p, t, f, a, e, n).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 6))).containsExactly(b, p, t, f, a, n).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 5))).containsExactly(b, p, t, f, a).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 4))).containsExactly(b, p, t, f).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 3))).containsExactly(b, p, t).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 2))).containsExactly(b, p).inOrder()
        assertThat(kinds(fitGaugeRows(eightRows, 1))).containsExactly(b)
    }

    @Test
    fun `the later event goes before the earlier one`() {
        val kept = fitGaugeRows(eightRows, 7).filter { it.kind == GaugeRowKind.EVENT }

        assertThat(kept).containsExactly(GaugeRow(GaugeRowKind.EVENT, "LA 10:00", "Hammaslääkäri"))
    }

    @Test
    fun `the battery row never drops`() {
        assertThat(kinds(fitGaugeRows(eightRows, 0))).containsExactly(GaugeRowKind.BATTERY)
        assertThat(kinds(fitGaugeRows(eightRows, -3))).containsExactly(GaugeRowKind.BATTERY)
    }
}
