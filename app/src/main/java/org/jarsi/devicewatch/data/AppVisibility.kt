package org.jarsi.devicewatch.data

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app's own screen is in front. Android grants a "while in use"
 * permission only to an app on screen (or to a foreground service declared for
 * that use, which the monitor is not), and writes every other ask down as a
 * refusal in its privacy log. The reads Android checks the location permission
 * for ([LocationGatedReads]) wait on this, so the app does not show up in its own
 * privacy log as an app trying for the location from the background.
 */
@Singleton
class AppVisibility @Inject constructor() {

    private val started = AtomicInteger(0)

    /** True from the activity's start to its stop. */
    val visible: Boolean get() = started.get() > 0

    fun onStart() {
        started.incrementAndGet()
    }

    fun onStop() {
        started.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}
