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

/**
 * Codex round 10: the row took eight groups unconditionally, which overflows a 344 dp clock
 * block once counts are two digits. The fit keeps whole groups from the front and reserves
 * room for the "+N" label whenever something is folded away.
 */
class DreamNotificationRowFitTest {

    private fun group(packageName: String, count: Int = 1) =
        NotificationIconGroup(packageName = packageName, count = count, newestPostTime = 1L, smallIcon = null)

    // icon 20, spacing 10, count gap 3, count label 10 per digit, "+N" label 12 + 10 per digit
    private fun fit(groups: List<NotificationIconGroup>, available: Float, maxGroups: Int = 8) =
        fitNotificationRow(
            groups = groups,
            availableWidth = available,
            iconWidth = 20f,
            spacing = 10f,
            countGap = 3f,
            countWidth = { count -> 10f * count.toString().length },
            overflowWidth = { hidden -> 12f + 10f * hidden.toString().length },
            maxGroups = maxGroups,
        )

    @Test
    fun `everything is shown when it fits`() {
        val groups = listOf(group("a"), group("b"), group("c"))
        // 3 icons + 2 gaps = 60 + 20 = 80
        val fit = fit(groups, available = 80f)
        assertThat(fit.shown).isEqualTo(groups)
        assertThat(fit.hidden).isEqualTo(0)
    }

    @Test
    fun `a narrow row drops groups from the end and reserves the overflow label`() {
        val groups = listOf(group("a"), group("b"), group("c"), group("d"))
        // 4 icons need 110. At 100: 3 icons + "+1" = 60 + 20 + 10 + 22 = 112 > 100;
        // 2 icons + "+2" = 40 + 10 + 10 + 22 = 82 <= 100.
        val fit = fit(groups, available = 100f)
        assertThat(fit.shown.map { it.packageName }).containsExactly("a", "b").inOrder()
        assertThat(fit.hidden).isEqualTo(2)
    }

    @Test
    fun `a count label widens its group`() {
        val groups = listOf(group("a", count = 12), group("b"), group("c"))
        // a = 20 + 3 + 20 = 43; all three = 43 + 10 + 20 + 10 + 20 = 103;
        // a, b and "+1" = 43 + 10 + 20 + 10 + 22 = 105; a and "+2" = 43 + 10 + 22 = 75
        assertThat(fit(groups, available = 103f).hidden).isEqualTo(0)
        assertThat(fit(groups, available = 102f)).isEqualTo(NotificationRowFit(listOf(groups[0]), 2))
    }

    @Test
    fun `the group cap folds the rest away even when the width allows more`() {
        val groups = (1..10).map { group("app$it") }
        val fit = fit(groups, available = 10_000f, maxGroups = 8)
        assertThat(fit.shown).hasSize(8)
        assertThat(fit.hidden).isEqualTo(2)
    }

    @Test
    fun `no width shows only the overflow label, and no groups show nothing`() {
        val groups = listOf(group("a"), group("b"))
        val fit = fit(groups, available = 25f)
        assertThat(fit.shown).isEmpty()
        assertThat(fit.hidden).isEqualTo(2)
        assertThat(fit(emptyList(), available = 100f)).isEqualTo(NotificationRowFit(emptyList(), 0))
    }
}
