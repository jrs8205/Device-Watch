package org.jarsi.devicewatch.system.dream

/** What a row of the Gauges style tells. The order here is the order on the screen. */
internal enum class GaugeRowKind {
    BATTERY,
    POWER,
    TEMPERATURE,
    FULL,
    ALARM,

    /** A calendar event: its time as the label, its title as the value. */
    EVENT,

    /** The unread notifications; the value is empty and the icons are drawn in its place. */
    NOTIFICATIONS,
}

/** One named row: a label on the left and a value on the right. */
internal data class GaugeRow(val kind: GaugeRowKind, val label: String, val value: String)

/** The rows' labels in the user's language. */
internal data class GaugeLabels(
    val battery: String,
    val power: String,
    val temperature: String,
    val full: String,
    val alarm: String,
    val notifications: String,
)

/**
 * The rows the Gauges style lists for a state. A row with nothing to say is left out
 * rather than shown empty: the power and the estimate exist only while the battery is
 * charging, the alarm only when one is set, the notifications only when some wait. A row
 * whose switch is off is left out too. [percent] is the level as the language writes it.
 */
internal fun gaugeRows(
    state: DreamUiState,
    options: DreamOptions,
    labels: GaugeLabels,
    percent: String,
): List<GaugeRow> = buildList {
    add(GaugeRow(GaugeRowKind.BATTERY, labels.battery, "$percent ${state.chargeState.icon}"))
    if (options.shows(DreamField.BATTERY_DETAILS)) {
        state.wattsText?.let { add(GaugeRow(GaugeRowKind.POWER, labels.power, it)) }
        add(GaugeRow(GaugeRowKind.TEMPERATURE, labels.temperature, state.tempText))
    }
    if (options.shows(DreamField.ESTIMATE) && state.fullClockText.isNotEmpty()) {
        add(GaugeRow(GaugeRowKind.FULL, labels.full, "~" + state.fullClockText))
    }
    if (options.shows(DreamField.ALARM) && state.alarmText.isNotEmpty()) {
        add(GaugeRow(GaugeRowKind.ALARM, labels.alarm, state.alarmText))
    }
    if (options.shows(DreamField.CALENDAR)) {
        for (event in state.calendarRows) {
            add(GaugeRow(GaugeRowKind.EVENT, event.whenText, event.title))
        }
    }
    if (options.shows(DreamField.NOTIFICATIONS) && state.notificationGroups.isNotEmpty()) {
        add(GaugeRow(GaugeRowKind.NOTIFICATIONS, labels.notifications, ""))
    }
}

/** What gives way first when the rows do not all fit; the battery row never does. */
private val GAUGE_DROP_ORDER = listOf(
    GaugeRowKind.EVENT,
    GaugeRowKind.NOTIFICATIONS,
    GaugeRowKind.ALARM,
    GaugeRowKind.FULL,
    GaugeRowKind.TEMPERATURE,
    GaugeRowKind.POWER,
)

/**
 * Keeps at most [maxRows] of [rows], in their order. The calendar events go first, the
 * later one before the earlier, then the other rows from the least to the most important.
 * The battery row stays whatever [maxRows] says.
 */
internal fun fitGaugeRows(rows: List<GaugeRow>, maxRows: Int): List<GaugeRow> {
    val limit = maxOf(maxRows, 1)
    val kept = rows.toMutableList()
    for (kind in GAUGE_DROP_ORDER) {
        while (kept.size > limit) {
            val index = kept.indexOfLast { it.kind == kind }
            if (index < 0) break
            kept.removeAt(index)
        }
    }
    return kept
}
