package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.KernelSysfsParser
import org.jarsi.devicewatch.data.SuspendStats
import org.jarsi.devicewatch.data.WakeCategory
import org.jarsi.devicewatch.data.WakeFixtures
import org.jarsi.devicewatch.data.WakeGlossary
import org.jarsi.devicewatch.data.WakeSnapshot
import org.jarsi.devicewatch.data.WakeupSource
import org.junit.Test

class WakePresentationTest {

    private val wake = KernelSysfsParser.parseWake(WakeFixtures.PIXEL_8A_WAKE)

    private fun source(index: Int, name: String, wakeups: Long = 0L, total: Long = 0L, active: Long = 0L) =
        WakeupSource(index, name, 0L, 0L, wakeups, 0L, active, total, 0L, 0L)

    @Test
    fun `an epoll source is the process behind it, cut before the file it waited on`() {
        assertThat(WakePresentation.kindOf("epolld4:android.hardwar.NETLINK")).isEqualTo(WakeupSourceKind.Process("android.hardwar"))
        assertThat(WakePresentation.kindOf("epolld6:android.hardwar.[timerfd11:android.hardwar]")).isEqualTo(WakeupSourceKind.Process("android.hardwar"))
        assertThat(WakePresentation.kindOf("epollm5:android.hardwar")).isEqualTo(WakeupSourceKind.Process("android.hardwar"))
        assertThat(WakePresentation.kindOf("epolld34:system_server.[timerfd41:system_server]")).isEqualTo(WakeupSourceKind.Process("system_server"))
        assertThat(WakePresentation.kindOf("epollm23:system_server")).isEqualTo(WakeupSourceKind.Process("system_server"))
        assertThat(WakePresentation.kindOf("epolld14:UsfTransport.acd-com.google.usf")).isEqualTo(WakeupSourceKind.Process("UsfTransport"))
        assertThat(WakePresentation.kindOf("epolld22:android.hardwar.acd-chre_data_rx")).isEqualTo(WakeupSourceKind.Process("android.hardwar"))
        assertThat(WakePresentation.kindOf("epollm9:audioserver")).isEqualTo(WakeupSourceKind.Process("audioserver"))
    }

    @Test
    fun `the system and the hardware services have plain names, other processes keep their own`() {
        assertThat(WakePresentation.processNameRes("system_server")).isNotNull()
        assertThat(WakePresentation.processNameRes("android.hardwar")).isNotNull()
        assertThat(WakePresentation.processNameRes("android.hardware.sensors")).isNotNull()
        assertThat(WakePresentation.processNameRes("audioserver")).isNull()
    }

    @Test
    fun `the pixel's kernel names fall into the glossary's parts`() {
        val expected = mapOf(
            "gpio_keys" to WakeCategory.INPUT,
            "gti.0" to WakeCategory.INPUT,
            "wlan_wake" to WakeCategory.WIFI,
            "wlan_rx_wake" to WakeCategory.WIFI,
            "s2mpg14-rtc" to WakeCategory.ALARM,
            "alarmtimer.1.auto" to WakeCategory.ALARM,
            "google-charger" to WakeCategory.CHARGING,
            "google-battery" to WakeCategory.CHARGING,
            "max_fg_irq" to WakeCategory.CHARGING,
            "s5100_wake_lock" to WakeCategory.MODEM,
            "cp2ap_wakeup" to WakeCategory.MODEM,
            "umts_ipc0" to WakeCategory.MODEM,
            "oem_ipc0" to WakeCategory.MODEM,
            "rmnet0" to WakeCategory.MODEM,
            "odm:btbcm" to WakeCategory.BLUETOOTH,
            "bthal_wakelock" to WakeCategory.BLUETOOTH,
            "com.google.usf" to WakeCategory.SENSORS,
            "com.google.chre" to WakeCategory.SENSORS,
            "MAILBOX_AOCA322APx100" to WakeCategory.SENSORS,
            "sound-aoc" to WakeCategory.AUDIO,
            "110f0000.drmdp" to WakeCategory.SCREEN,
        )
        for ((name, category) in expected) {
            assertThat(WakePresentation.kindOf(name)).isEqualTo(WakeupSourceKind.Part(category))
        }
        assertThat(WakePresentation.kindOf("tpu:android.hardwar.1-1118")).isEqualTo(WakeupSourceKind.Unknown)
        assertThat(WakeGlossary.categoryOf("PowerManagerService.WakeLocks")).isEqualTo(WakeCategory.APPS)
    }

    @Test
    fun `the top wakers are the sources that woke the phone, most often first, and only those`() {
        val top = WakePresentation.topWakers(wake)
        assertThat(top).isNotEmpty()
        assertThat(top.size).isAtMost(WakePresentation.TOP_WAKERS)
        assertThat(top.all { it.wakeupCount > 0L }).isTrue()
        assertThat(top.map { it.wakeupCount }).isInOrder(reverseOrder<Long>())
        assertThat(top.first().name).isEqualTo("google-charger")
    }

    @Test
    fun `the longest holders are by total time, the active ones by how long they have held so far`() {
        val snapshot = WakeSnapshot(
            sources = listOf(
                source(1, "a", total = 10L),
                source(2, "b", total = 300L, active = 5L),
                source(3, "c", total = 0L),
                source(4, "d", total = 300L, active = 50L),
            ),
            suspend = null,
        )
        assertThat(WakePresentation.longestHolders(snapshot).map { it.name }).containsExactly("b", "d", "a").inOrder()
        assertThat(WakePresentation.longestHolders(snapshot, limit = 1).map { it.name }).containsExactly("b")
        assertThat(WakePresentation.activeNow(snapshot).map { it.name }).containsExactly("d", "b").inOrder()
        assertThat(WakePresentation.activeNow(wake).map { it.name }).containsExactly("wlan_rx_wake")
    }

    @Test
    fun `failed steps list only the ones that failed, in the order of a sleep attempt`() {
        val stats = SuspendStats(340, 72, failedFreeze = 30, failedPrepare = 0, failedSuspend = 23, failedSuspendLate = 0,
            failedSuspendNoirq = 0, failedResume = 1, failedResumeEarly = 0, failedResumeNoirq = 0,
            lastFailedDev = "alarmtimer.1.auto", lastFailedErrno = -16, lastFailedStep = "freeze")
        assertThat(WakePresentation.failedSteps(stats)).containsExactly(
            SuspendStep.FREEZE to 30L, SuspendStep.SUSPEND to 23L, SuspendStep.RESUME to 1L,
        ).inOrder()
        assertThat(WakePresentation.stepOf("freeze")).isEqualTo(SuspendStep.FREEZE)
        assertThat(WakePresentation.stepOf("suspend_noirq")).isEqualTo(SuspendStep.SUSPEND_NOIRQ)
        assertThat(WakePresentation.stepOf("")).isNull()
        assertThat(WakePresentation.stepOf(null)).isNull()
        assertThat(SuspendStep.entries.map(WakePresentation::stepExplanationRes).toSet()).hasSize(4)
    }

    @Test
    fun `every step has a plain name, the three suspend steps and the three resume steps sharing theirs`() {
        assertThat(SuspendStep.entries.map(WakePresentation::stepNameRes).toSet()).hasSize(4)
        assertThat(WakePresentation.stepNameRes(SuspendStep.SUSPEND)).isEqualTo(WakePresentation.stepNameRes(SuspendStep.SUSPEND_NOIRQ))
        assertThat(WakePresentation.stepNameRes(SuspendStep.RESUME)).isEqualTo(WakePresentation.stepNameRes(SuspendStep.RESUME_EARLY))
        assertThat(WakePresentation.stepNameRes(SuspendStep.FREEZE)).isNotEqualTo(WakePresentation.stepNameRes(SuspendStep.PREPARE))
    }
}
