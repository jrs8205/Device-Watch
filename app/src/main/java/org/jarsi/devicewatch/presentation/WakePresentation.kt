package org.jarsi.devicewatch.presentation

import androidx.annotation.StringRes
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.SuspendStats
import org.jarsi.devicewatch.data.WakeCategory
import org.jarsi.devicewatch.data.WakeGlossary
import org.jarsi.devicewatch.data.WakeSnapshot
import org.jarsi.devicewatch.data.WakeupSource

/** What a wakeup source's kernel name says it is, as far as the glossary can tell. */
sealed interface WakeupSourceKind {
    /** A process waiting to be woken for a timer or a message (an epoll wakeup source), by the process's name. */
    data class Process(val process: String) : WakeupSourceKind

    /** A part of the phone the glossary knows. */
    data class Part(val category: WakeCategory) : WakeupSourceKind

    /** A name that says nothing the glossary knows. */
    data object Unknown : WakeupSourceKind
}

/** The steps of a sleep attempt, as `/sys/power/suspend_stats` counts their failures. */
enum class SuspendStep { FREEZE, PREPARE, SUSPEND, SUSPEND_LATE, SUSPEND_NOIRQ, RESUME_NOIRQ, RESUME_EARLY, RESUME }

/** The pure half of the Kernel page's sleep section. */
object WakePresentation {

    const val TOP_WAKERS = 8
    const val LONGEST_HOLDERS = 5

    /** `epolld4:android.hardwar.NETLINK`, `epollm23:system_server`: the fd's owner, then what the fd was. */
    private val EPOLL = Regex("""^epoll[dm]\d+:(.+)$""")

    /** Where the owner's name ends and the fd's begins, in the forms seen so far. */
    private val FD_MARKERS = listOf(".[", ".acd-", ".NETLINK")

    private const val SYSTEM_SERVER = "system_server"
    private const val HAL_PREFIX = "android.hardwar"

    fun kindOf(name: String): WakeupSourceKind {
        EPOLL.matchEntire(name)?.let { match ->
            val raw = match.groupValues[1]
            val cut = FD_MARKERS.map { raw.indexOf(it) }.filter { it > 0 }.minOrNull()
            return WakeupSourceKind.Process(if (cut == null) raw else raw.substring(0, cut))
        }
        return WakeGlossary.categoryOf(name)?.let(WakeupSourceKind::Part) ?: WakeupSourceKind.Unknown
    }

    /** The plain name for a process a wakeup source belongs to; null when only its own name will do. */
    @StringRes
    fun processNameRes(process: String): Int? = when {
        process == SYSTEM_SERVER -> R.string.wake_process_system
        process.startsWith(HAL_PREFIX) -> R.string.wake_process_hal
        else -> null
    }

    /** The sources that woke the phone from sleep, most often first. */
    fun topWakers(snapshot: WakeSnapshot, limit: Int = TOP_WAKERS): List<WakeupSource> =
        snapshot.sources.filter { it.wakeupCount > 0L }.sortedWith(compareByDescending<WakeupSource> { it.wakeupCount }.thenBy { it.index }).take(limit)

    /** The sources that held the phone awake longest in all. */
    fun longestHolders(snapshot: WakeSnapshot, limit: Int = LONGEST_HOLDERS): List<WakeupSource> =
        snapshot.sources.filter { it.totalTimeMs > 0L }.sortedWith(compareByDescending<WakeupSource> { it.totalTimeMs }.thenBy { it.index }).take(limit)

    /** The sources holding the phone awake at the moment of the read, longest hold first. */
    fun activeNow(snapshot: WakeSnapshot): List<WakeupSource> =
        snapshot.sources.filter { it.activeNow }.sortedWith(compareByDescending<WakeupSource> { it.activeTimeMs }.thenBy { it.index })

    /** The steps at which sleep attempts failed, with their counts; steps that never failed are left out. */
    fun failedSteps(stats: SuspendStats): List<Pair<SuspendStep, Long>> = listOf(
        SuspendStep.FREEZE to stats.failedFreeze,
        SuspendStep.PREPARE to stats.failedPrepare,
        SuspendStep.SUSPEND to stats.failedSuspend,
        SuspendStep.SUSPEND_LATE to stats.failedSuspendLate,
        SuspendStep.SUSPEND_NOIRQ to stats.failedSuspendNoirq,
        SuspendStep.RESUME_NOIRQ to stats.failedResumeNoirq,
        SuspendStep.RESUME_EARLY to stats.failedResumeEarly,
        SuspendStep.RESUME to stats.failedResume,
    ).filter { it.second > 0L }

    /** The kernel's name for a step, `freeze` or `suspend_noirq`, to the step; null for one it does not name. */
    fun stepOf(name: String?): SuspendStep? = when (name) {
        "freeze" -> SuspendStep.FREEZE
        "prepare" -> SuspendStep.PREPARE
        "suspend" -> SuspendStep.SUSPEND
        "suspend_late" -> SuspendStep.SUSPEND_LATE
        "suspend_noirq" -> SuspendStep.SUSPEND_NOIRQ
        "resume_noirq" -> SuspendStep.RESUME_NOIRQ
        "resume_early" -> SuspendStep.RESUME_EARLY
        "resume" -> SuspendStep.RESUME
        else -> null
    }

    /** What going wrong at a step means, in plain words. */
    @StringRes
    fun stepExplanationRes(step: SuspendStep): Int = when (step) {
        SuspendStep.FREEZE -> R.string.kernel_sleep_step_freeze
        SuspendStep.PREPARE -> R.string.kernel_sleep_step_prepare
        SuspendStep.SUSPEND, SuspendStep.SUSPEND_LATE, SuspendStep.SUSPEND_NOIRQ -> R.string.kernel_sleep_step_suspend
        SuspendStep.RESUME, SuspendStep.RESUME_EARLY, SuspendStep.RESUME_NOIRQ -> R.string.kernel_sleep_step_resume
    }
}
