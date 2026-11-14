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
    DreamStyle.GLOW -> field != DreamField.CALENDAR
    DreamStyle.MINIMAL ->
        field == DreamField.DATE || field == DreamField.NOTIFICATIONS || field == DreamField.BATTERY_DETAILS
}
