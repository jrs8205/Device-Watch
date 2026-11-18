package org.jarsi.devicewatch.system.dream

/**
 * The looks the screensaver can take. [id] is what the settings store, so it never changes.
 * An id this version does not know (a backup made by a newer one) falls back to [GLOW].
 */
internal enum class DreamStyle(val id: String) {
    /** The look of 1.11.0, and the default. */
    GLOW("glow"),

    /** A thin clock on black, one hairline for the battery and one line of figures. */
    MINIMAL("minimal"),

    /** Dim red on black for a bedroom: the clock, the next alarm and the battery. */
    NIGHT("night"),

    /** The clock inside a ring that fills with the battery. */
    RING("ring"),

    /** The app's own look: monospaced figures on named rows, calendar events among them. */
    GAUGES("gauges"),

    /** The battery's level as a bright line round the screen's edge. */
    EDGE("edge"),
}

internal fun dreamStyleOf(id: String?): DreamStyle =
    DreamStyle.entries.firstOrNull { it.id == id } ?: DreamStyle.GLOW

/** A piece of information a style may show. Each has a switch in the settings. */
internal enum class DreamField {
    DEVICE_NAME,
    SECONDS,
    DATE,
    ALARM,
    NOTIFICATIONS,
    BATTERY_DETAILS,
    ESTIMATE,
    CALENDAR,
}

/** Whether the style has a place for the field at all, whatever its switch says. */
internal fun DreamStyle.supports(field: DreamField): Boolean = when (this) {
    // Ring was first drawn without the name, the alarm and the estimate (GitHub #11).
    DreamStyle.GLOW, DreamStyle.EDGE, DreamStyle.RING -> field != DreamField.CALENDAR
    DreamStyle.MINIMAL ->
        field == DreamField.DATE || field == DreamField.NOTIFICATIONS || field == DreamField.BATTERY_DETAILS
    DreamStyle.NIGHT -> field == DreamField.ALARM
    DreamStyle.GAUGES -> true
}
