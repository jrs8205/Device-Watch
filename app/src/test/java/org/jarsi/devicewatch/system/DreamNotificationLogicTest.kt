package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM tests for the screensaver's notification row: which active notifications
 * count as "unread" for the row, how they collapse to one icon per app, and which
 * arrivals should pulse the row.
 */
class DreamNotificationLogicTest {

    private fun active(
        key: String,
        packageName: String = "com.example.app",
        postTime: Long = 1_000L,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        isClearable: Boolean = true,
    ) = ActiveNotification(
        key = key,
        packageName = packageName,
        postTime = postTime,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
        isClearable = isClearable,
        smallIcon = null,
    )

    @Test
    fun `ongoing, group-summary and non-clearable notifications are not shown`() {
        val groups = visibleNotificationGroups(
            listOf(
                active("a", isOngoing = true),
                active("b", isGroupSummary = true),
                active("c", isClearable = false),
                active("d"),
            )
        )
        assertThat(groups).hasSize(1)
        assertThat(groups.single().count).isEqualTo(1)
    }

    @Test
    fun `notifications collapse to one group per app with a count`() {
        val groups = visibleNotificationGroups(
            listOf(
                active("a", packageName = "com.mail", postTime = 10),
                active("b", packageName = "com.mail", postTime = 30),
                active("c", packageName = "com.chat", postTime = 20),
            )
        )
        assertThat(groups.map { it.packageName }).containsExactly("com.mail", "com.chat").inOrder()
        assertThat(groups.first().count).isEqualTo(2)
        assertThat(groups.first().newestPostTime).isEqualTo(30)
    }

    @Test
    fun `groups are ordered newest first`() {
        val groups = visibleNotificationGroups(
            listOf(
                active("a", packageName = "com.old", postTime = 10),
                active("b", packageName = "com.new", postTime = 50),
                active("c", packageName = "com.mid", postTime = 30),
            )
        )
        assertThat(groups.map { it.packageName }).containsExactly("com.new", "com.mid", "com.old").inOrder()
    }

    @Test
    fun `empty input gives an empty row`() {
        assertThat(visibleNotificationGroups(emptyList())).isEmpty()
    }

    @Test
    fun `only keys that were not visible before count as new arrivals`() {
        val before = setOf("a", "b")
        val after = setOf("b", "c", "d")
        assertThat(newArrivalKeys(previous = before, current = after)).containsExactly("c", "d")
    }

    @Test
    fun `a removal is not an arrival`() {
        assertThat(newArrivalKeys(previous = setOf("a", "b"), current = setOf("a"))).isEmpty()
    }

    @Test
    fun `visible keys ignore the notifications the row does not show`() {
        val keys = visibleNotificationKeys(
            listOf(active("shown"), active("ongoing", isOngoing = true))
        )
        assertThat(keys).containsExactly("shown")
    }
}
