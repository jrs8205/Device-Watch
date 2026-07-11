package com.example.modernwidget.presentation.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SinceChargeNoticesTest {

    private val weekMs = 7L * 24 * 60 * 60 * 1000

    @Test
    fun `usage access notice shows only when an anchor exists and access is missing`() {
        assertThat(SinceChargeNotices.showUsageAccessNotice(hasAnchor = true, hasUsageAccess = false)).isTrue()
        assertThat(SinceChargeNotices.showUsageAccessNotice(hasAnchor = true, hasUsageAccess = true)).isFalse()
        assertThat(SinceChargeNotices.showUsageAccessNotice(hasAnchor = false, hasUsageAccess = false)).isFalse()
    }

    @Test
    fun `stale notice shows once the anchor is over a week old`() {
        assertThat(
            SinceChargeNotices.showStaleNotice(hasAnchor = true, nowMillis = weekMs + 1_001, anchorMillis = 1_000)
        ).isTrue()
        assertThat(
            SinceChargeNotices.showStaleNotice(hasAnchor = true, nowMillis = weekMs + 1_000, anchorMillis = 1_000)
        ).isFalse()
        assertThat(
            SinceChargeNotices.showStaleNotice(hasAnchor = false, nowMillis = weekMs + 1_001, anchorMillis = 1_000)
        ).isFalse()
    }
}
