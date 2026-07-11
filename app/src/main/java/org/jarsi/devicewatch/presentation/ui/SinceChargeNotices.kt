package org.jarsi.devicewatch.presentation.ui

/**
 * Visibility rules for the since-charge page's data-quality notices. Pure logic —
 * kept out of the composable so the missing/stale cases stay unit-testable. Both
 * notices must show precisely when the screen-time card CANNOT (no data), which is
 * why they are independent of whether any donut segments exist.
 */
object SinceChargeNotices {

    /** Android keeps detailed usage events for roughly a week; older periods undercount. */
    const val STALE_EVENTS_MS = 7L * 24 * 60 * 60 * 1000

    fun showUsageAccessNotice(hasAnchor: Boolean, hasUsageAccess: Boolean): Boolean =
        hasAnchor && !hasUsageAccess

    fun showStaleNotice(hasAnchor: Boolean, nowMillis: Long, anchorMillis: Long): Boolean =
        hasAnchor && nowMillis - anchorMillis > STALE_EVENTS_MS
}
