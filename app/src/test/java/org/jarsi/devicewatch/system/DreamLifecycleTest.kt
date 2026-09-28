package org.jarsi.devicewatch.system

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * GitHub #4: on Android 14+ the dream's window can detach after onDestroy() has already
 * moved the lifecycle to DESTROYED; pausing a destroyed registry throws
 * "State is 'DESTROYED' and cannot be moved to STARTED". The driver must make every
 * callback safe in any order the framework delivers them.
 */
class DreamLifecycleTest {

    private class Owner : LifecycleOwner {
        val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun driver(): Pair<DreamLifecycleDriver, Owner> {
        val owner = Owner()
        return DreamLifecycleDriver(owner.registry) to owner
    }

    private fun record(owner: Owner): MutableList<Lifecycle.Event> {
        val events = mutableListOf<Lifecycle.Event>()
        owner.registry.addObserver(LifecycleEventObserver { _, event -> events += event })
        return events
    }

    @Test
    fun `the normal order creates, resumes, stops and destroys`() {
        val (driver, owner) = driver()
        val events = record(owner)
        driver.onCreate()
        driver.onAttachedToWindow()
        driver.onDetachedFromWindow()
        driver.onDestroy()
        assertThat(events).containsExactly(
            Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY,
        ).inOrder()
        assertThat(owner.registry.currentState).isEqualTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun `a detach that arrives after destroy does nothing`() {
        val (driver, owner) = driver()
        driver.onCreate()
        driver.onAttachedToWindow()
        driver.onDestroy()
        driver.onDetachedFromWindow()
        assertThat(owner.registry.currentState).isEqualTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun `destroy without a detach still stops first, and a second detach is a no-op`() {
        val (driver, owner) = driver()
        val events = record(owner)
        driver.onCreate()
        driver.onAttachedToWindow()
        driver.onDestroy()
        driver.onDetachedFromWindow()
        driver.onDetachedFromWindow()
        assertThat(events).containsExactly(
            Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY,
        ).inOrder()
    }

    @Test
    fun `a detach before any attach and an attach after destroy are ignored`() {
        val (driver, owner) = driver()
        driver.onCreate()
        driver.onDetachedFromWindow()
        assertThat(owner.registry.currentState).isEqualTo(Lifecycle.State.CREATED)
        driver.onDestroy()
        driver.onAttachedToWindow()
        assertThat(owner.registry.currentState).isEqualTo(Lifecycle.State.DESTROYED)
    }
}
