package org.jarsi.devicewatch.system

import org.jarsi.devicewatch.quick.QuickMetricLabels
import org.jarsi.devicewatch.quick.QuickMetricReading

/**
 * The live notification's two lines. Expanded, Android shows [title] above the
 * expanded text, so that text is [text] alone, wrapped to show every figure.
 */
data class LiveNotificationText(val title: String, val text: String?) {
    /** Both lines, which is what tells one posting from the next. */
    val content: String get() = listOfNotNull(title, text).joinToString("\n")
}

/**
 * The chosen figures as the notification shows them: each with its short name
 * ("CPU 23%"), three on the first line and the rest on the second, a missing one
 * left out. Null when nothing could be read, so the notification falls back to
 * the monitor's own words instead of an empty title.
 */
fun liveNotificationText(readings: List<QuickMetricReading>, labels: QuickMetricLabels): LiveNotificationText? {
    val entries = readings.filter { it.available }.map { reading ->
        "${labels.names.getValue(reading.metric).shortName} ${reading.value}"
    }
    if (entries.isEmpty()) return null
    val title = entries.take(FIRST_LINE).joinToString(SEPARATOR)
    val text = entries.drop(FIRST_LINE).joinToString(SEPARATOR).ifEmpty { null }
    return LiveNotificationText(title, text)
}

private const val FIRST_LINE = 3
private const val SEPARATOR = " · "

/**
 * When the live notification is posted. New text goes out once; the same text
 * again is skipped, so the notification neither flickers nor runs into Android's
 * update throttling. A swipe (Android 14 lets one take even this notification
 * away) holds it back until the screen next comes on; a change in Settings posts
 * at once whatever came before. Called from the service's main and worker threads.
 */
class LiveNotificationGate {
    private var lastPosted: String? = null
    private var dismissed = false

    @Synchronized
    fun onDismissed() {
        dismissed = true
    }

    @Synchronized
    fun onScreenOn() {
        dismissed = false
        lastPosted = null
    }

    @Synchronized
    fun onSettingsChanged() {
        dismissed = false
        lastPosted = null
    }

    /** What was posted is not on screen (it did not go out, or the system took it away); the same text may go up again. */
    @Synchronized
    fun notPosted() {
        lastPosted = null
    }

    /** Whether [content] should be posted now; a yes counts it as posted. */
    @Synchronized
    fun shouldPost(content: String): Boolean {
        if (dismissed || content == lastPosted) return false
        lastPosted = content
        return true
    }
}
