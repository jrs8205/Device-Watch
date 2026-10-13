package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

/** The wake read: its command, what the parser makes of the answer, and the source's gate. */
class WakeReadTest {

    private val wake = KernelSysfsParser.parseWake(WakeFixtures.PIXEL_8A_WAKE)

    @Test
    fun `the wake read is one grep over every wakeup source's counters and the suspend statistics`() {
        val command = KernelCommands.WAKE_READ
        assertThat(command).startsWith("grep -s -H '^' ")
        assertThat(command.split(' ').count { it.startsWith("/sys/class/wakeup/wakeup*/") }).isEqualTo(KernelCommands.WAKEUP_FILES.size)
        assertThat(command.split(' ').count { it.startsWith("/sys/power/suspend_stats/") }).isEqualTo(KernelCommands.SUSPEND_FILES.size)
        assertThat(command).contains("/sys/class/wakeup/wakeup*/wakeup_count")
        assertThat(command).contains("/sys/class/wakeup/wakeup*/active_time_ms")
        assertThat(command).contains("/sys/power/suspend_stats/last_failed_dev")
        assertThat(command).doesNotContain("prevent_suspend_time_ms")
        // Nothing but paths, globs and the grep itself: no quoting, no substitution.
        assertThat(command.removePrefix("grep -s -H '^' ")).matches("[A-Za-z0-9_/* .-]+")
    }

    @Test
    fun `reads the phone's wakeup sources with their names and counters`() {
        assertThat(wake.sources).hasSize(22)
        val keys = wake.sources.first { it.name == "gpio_keys" }
        assertThat(keys.index).isEqualTo(0)
        assertThat(keys.activeCount).isEqualTo(10L)
        assertThat(keys.wakeupCount).isEqualTo(0L)
        assertThat(keys.totalTimeMs).isEqualTo(53L)
        assertThat(keys.maxTimeMs).isEqualTo(5L)
        assertThat(keys.activeNow).isFalse()
        val charger = wake.sources.first { it.name == "google-charger" }
        assertThat(charger.wakeupCount).isEqualTo(375L)
        assertThat(charger.eventCount).isEqualTo(1911L)
        val alarm = wake.sources.first { it.name == "alarmtimer.1.auto" }
        assertThat(alarm.expireCount).isEqualTo(20L)
        assertThat(alarm.lastChangeMs).isEqualTo(6_850_529L)
    }

    @Test
    fun `a source being held right now says so`() {
        val rx = wake.sources.first { it.name == "wlan_rx_wake" }
        assertThat(rx.activeTimeMs).isEqualTo(80L)
        assertThat(rx.activeNow).isTrue()
        assertThat(wake.sources.count { it.activeNow }).isEqualTo(1)
    }

    @Test
    fun `names with colons and brackets stay whole`() {
        assertThat(wake.sources.map { it.name }).containsAtLeast(
            "epolld4:android.hardwar.NETLINK",
            "epolld34:system_server.[timerfd41:system_server]",
        )
    }

    @Test
    fun `reads the sleep statistics and what stopped the last failed attempt`() {
        val suspend = wake.suspend!!
        assertThat(suspend.success).isEqualTo(340L)
        assertThat(suspend.fail).isEqualTo(72L)
        assertThat(suspend.failedFreeze).isEqualTo(30L)
        assertThat(suspend.failedFreeze + suspend.failedSuspend + suspend.failedPrepare + suspend.failedSuspendLate +
            suspend.failedSuspendNoirq + suspend.failedResume + suspend.failedResumeEarly + suspend.failedResumeNoirq)
            .isAtMost(suspend.fail)
        assertThat(suspend.lastFailedDev).isNotEmpty()
        assertThat(suspend.lastFailedStep).isNotEmpty()
        assertThat(suspend.lastFailedErrno).isNotNull()
    }

    @Test
    fun `a source without a name is left out, a garbled counter reads as zero`() {
        val output = """
            /sys/class/wakeup/wakeup3/active_count:5
            /sys/class/wakeup/wakeup3/wakeup_count:2
            /sys/class/wakeup/wakeup7/name:thing
            /sys/class/wakeup/wakeup7/wakeup_count:lots
            /sys/class/wakeup/wakeup7/total_time_ms:-4
            /sys/class/wakeup/wakeup7/prevent_suspend_time_ms:9
            /sys/class/wakeup/wakeup7/subsystem:ignored
            /sys/power/suspend_stats/success:12
            /sys/power/suspend_stats/last_failed_dev:
        """.trimIndent()
        val parsed = KernelSysfsParser.parseWake(output)
        val thing = parsed.sources.single()
        assertThat(thing.index).isEqualTo(7)
        assertThat(thing.wakeupCount).isEqualTo(0L)
        assertThat(thing.totalTimeMs).isEqualTo(0L)
        assertThat(parsed.suspend!!.success).isEqualTo(12L)
        assertThat(parsed.suspend!!.fail).isEqualTo(0L)
        assertThat(parsed.suspend!!.lastFailedDev).isNull()
    }

    @Test
    fun `no output at all is an empty snapshot`() {
        val parsed = KernelSysfsParser.parseWake("")
        assertThat(parsed.sources).isEmpty()
        assertThat(parsed.suspend).isNull()
        assertThat(parsed.empty).isTrue()
        assertThat(wake.empty).isFalse()
    }

    @Test
    fun `paths outside the wake files are ignored`() {
        val output = """
            /sys/class/wakeup/wakeup1/name:real
            /sys/class/wakeup/other1/name:not a source
            /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1704000
            /sys/power/suspend_stats/../something:1
        """.trimIndent()
        val parsed = KernelSysfsParser.parseWake(output)
        assertThat(parsed.sources.map { it.name }).containsExactly("real")
        assertThat(parsed.suspend).isNull()
    }

    @Test
    fun `the source reads the wake snapshot through the root gate and nothing without root`() {
        val root = FakeSysfsRoot()
        val settings = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT)
        var screenOn = true
        val source = ShellKernelSnapshotSource(RootOnlyShell(settings, root) { screenOn })

        val read = source.readWake()!!
        assertThat(read.sources).hasSize(22)
        assertThat(root.commands).containsExactly(KernelCommands.WAKE_READ)

        screenOn = false
        assertThat(source.readWake()).isNull()
        screenOn = true
        settings.setPrivilegedAccess(PrivilegedAccess.SHIZUKU)
        assertThat(source.readWake()).isNull()
        assertThat(root.commands).hasSize(1)
    }

    @Test
    fun `a kernel without wakeup sources reads as empty, a root that does not answer as nothing`() {
        val root = FakeSysfsRoot()
        val source = ShellKernelSnapshotSource(RootOnlyShell(FakeAppSettingsRepository(access = PrivilegedAccess.ROOT), root) { true })
        root.wakeOutput = null
        assertThat(source.readWake()!!.empty).isTrue()
        root.loseAnswers = true
        assertThat(source.readWake()).isNull()
    }
}
