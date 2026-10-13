package org.jarsi.devicewatch.data

/**
 * One of the kernel's wakeup sources: a driver, a timer or a process that can
 * hold the phone awake or wake it from sleep. Every count is since the phone
 * started, as the files under `/sys/class/wakeup/wakeupN` keep them.
 */
data class WakeupSource(
    val index: Int,
    val name: String,
    /** Times it was held. */
    val activeCount: Long,
    /** Events it signalled, held or not. */
    val eventCount: Long,
    /** Times it was what woke the phone from sleep. */
    val wakeupCount: Long,
    /** Times a timed hold ran out. */
    val expireCount: Long,
    /** How long it has been held right now; 0 when it is not. */
    val activeTimeMs: Long,
    /** How long it has held the phone awake in all. */
    val totalTimeMs: Long,
    /** Its longest single hold. */
    val maxTimeMs: Long,
    /** When it last changed, in milliseconds since boot. */
    val lastChangeMs: Long,
) {
    val activeNow: Boolean get() = activeTimeMs > 0L
}

/** The kernel's own tally of its attempts to sleep, from `/sys/power/suspend_stats`. */
data class SuspendStats(
    val success: Long,
    val fail: Long,
    val failedFreeze: Long,
    val failedPrepare: Long,
    val failedSuspend: Long,
    val failedSuspendLate: Long,
    val failedSuspendNoirq: Long,
    val failedResume: Long,
    val failedResumeEarly: Long,
    val failedResumeNoirq: Long,
    /** The device or source that stopped the last failed attempt, as the kernel names it. */
    val lastFailedDev: String?,
    val lastFailedErrno: Int?,
    /** The step the last failed attempt stopped at: `freeze`, `suspend`, `prepare` and so on. */
    val lastFailedStep: String?,
)

/** What one wake read brought back. */
data class WakeSnapshot(
    val sources: List<WakeupSource>,
    val suspend: SuspendStats?,
) {
    val empty: Boolean get() = sources.isEmpty() && suspend == null
}
