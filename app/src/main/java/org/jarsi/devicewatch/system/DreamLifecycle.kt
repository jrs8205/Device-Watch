package org.jarsi.devicewatch.system

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry

/**
 * Drives the screensaver's [LifecycleRegistry] from the DreamService callbacks, in whatever
 * order the framework delivers them.
 *
 * GitHub #4: on Android 14+ the dream is hosted by a framework DreamActivity whose window can
 * be detached *after* [onDestroy] has already run. Pausing a DESTROYED registry throws
 * "State is 'DESTROYED' and cannot be moved to STARTED", which crashed the app on GrapheneOS
 * when the phone was locked right after plugging in. Every step here first checks that the
 * registry can still take it.
 */
internal class DreamLifecycleDriver(private val registry: LifecycleRegistry) {

    fun onCreate() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** Starts and resumes, unless the dream was already destroyed. */
    fun onAttachedToWindow() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Pauses and stops only a lifecycle that is actually running. */
    fun onDetachedFromWindow() {
        if (!registry.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /** Stops first when the window never detached, then destroys; a second call is a no-op. */
    fun onDestroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        onDetachedFromWindow()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
