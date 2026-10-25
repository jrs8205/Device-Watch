package org.jarsi.devicewatch.system

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.data.SystemStats

/**
 * Keeps the monitor's notification in step with the figures chosen in Settings.
 * Readings and changes of choice take turns under one lock, and the choice is
 * read inside it: a reading begun under the old choice can never post its live
 * notification after the silent one that clearing the last figure put up.
 */
class LiveNotificationController(
    private val chosen: () -> Set<QuickMetric>,
    private val read: suspend (Set<QuickMetric>, SystemStats) -> LiveNotificationText?,
    /** Posts the live notification; false when it could not be posted (no permission, notifications off). */
    private val postLive: (LiveNotificationText?) -> Boolean,
    private val postQuiet: () -> Unit,
) {
    private val gate = LiveNotificationGate()
    private val mutex = Mutex()

    fun onDismissed() = gate.onDismissed()

    fun onScreenOn() = gate.onScreenOn()

    /** Shows [stats] in the live notification when figures are chosen; nothing otherwise. */
    suspend fun refresh(stats: SystemStats) = mutex.withLock {
        val selection = chosen()
        if (selection.isEmpty()) return@withLock
        val text = read(selection, stats)
        // One that did not go out is tried again with the next reading, so the
        // figures appear as soon as notifications are allowed.
        if (gate.shouldPost(text?.content.orEmpty()) && !postLive(text)) gate.notPosted()
    }

    /**
     * The choice changed: with nothing chosen the silent notification goes back up
     * at once; with figures chosen the caller follows with a fresh reading, which
     * this returns true for.
     */
    suspend fun onSelectionChanged(): Boolean = mutex.withLock {
        gate.onSettingsChanged()
        if (chosen().isEmpty()) {
            postQuiet()
            false
        } else {
            true
        }
    }
}
