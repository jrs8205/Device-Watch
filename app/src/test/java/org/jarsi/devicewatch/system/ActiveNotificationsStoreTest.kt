package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/** The process-wide snapshot of active notifications the listener keeps for the screensaver. */
class ActiveNotificationsStoreTest {

    private fun active(key: String, postTime: Long = 1L) = ActiveNotification(
        key = key,
        packageName = "com.example",
        postTime = postTime,
        isOngoing = false,
        isGroupSummary = false,
        isClearable = true,
        smallIcon = null,
    )

    @After
    fun tearDown() {
        ActiveNotificationsStore.clear()
    }

    @Test
    fun `put adds and replaces by key, remove drops, clear empties`() {
        ActiveNotificationsStore.put(active("a", postTime = 1))
        ActiveNotificationsStore.put(active("b", postTime = 2))
        ActiveNotificationsStore.put(active("a", postTime = 3))
        assertThat(ActiveNotificationsStore.notifications.value.map { it.key to it.postTime })
            .containsExactly("a" to 3L, "b" to 2L)

        ActiveNotificationsStore.remove("a")
        assertThat(ActiveNotificationsStore.notifications.value.map { it.key }).containsExactly("b")

        ActiveNotificationsStore.clear()
        assertThat(ActiveNotificationsStore.notifications.value).isEmpty()
    }

    @Test
    fun `replaceAll swaps the whole snapshot`() {
        ActiveNotificationsStore.put(active("stale"))
        ActiveNotificationsStore.replaceAll(listOf(active("x"), active("y")))
        assertThat(ActiveNotificationsStore.notifications.value.map { it.key }).containsExactly("x", "y")
    }
}
