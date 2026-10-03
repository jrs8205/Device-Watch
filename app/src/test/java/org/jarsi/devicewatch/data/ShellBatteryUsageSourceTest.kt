package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

class ShellBatteryUsageSourceTest {

    /** Answers with [answer] and counts the dumps it was asked for. */
    private class ScriptedShell(var answer: String?) : PrivilegedShell {
        var asked = 0
        override fun run(command: String, timeoutMillis: Long): String? {
            asked++
            return answer
        }
        override fun close() = Unit
    }

    private val dump = """
        Statistics since last charge:
          Estimated battery capacity: 4000 mAh
          Time on battery: 1h 0m 0s 0ms (96,6%) realtime, 50m 0s 0ms (85,4%) uptime
          Discharge: 100 mAh
    """.trimIndent()

    private var screenOn = true
    private var now = 1_000L

    private fun source(
        shell: PrivilegedShell,
        settings: AppSettingsRepository = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT),
    ) = ShellBatteryUsageSource(settings, shell, screenOn = { screenOn }, now = { now }, label = { "uid-$it" })

    @Test
    fun `with the screen off no dump is asked for, and the last one stands`() {
        // The since-charge page keeps refreshing under a locked screen.
        val shell = ScriptedShell(dump)
        val source = source(shell)

        screenOn = false
        assertThat(source.sinceCharge()).isNull()
        assertThat(shell.asked).isEqualTo(0)

        screenOn = true
        val report = source.sinceCharge()
        assertThat(report).isNotNull()
        assertThat(shell.asked).isEqualTo(1)

        // Long after the last dump went stale, still nothing while the screen is off.
        now += 10 * 60_000L
        screenOn = false
        assertThat(source.sinceCharge()).isSameInstanceAs(report)
        assertThat(shell.asked).isEqualTo(1)

        screenOn = true
        source.sinceCharge()
        assertThat(shell.asked).isEqualTo(2)
    }

    @Test
    fun `one dump answers a minute of refreshes, and so does one that failed`() {
        val shell = ScriptedShell(answer = null)
        val source = source(shell)

        assertThat(source.sinceCharge()).isNull()
        now += 30_000L
        shell.answer = dump
        assertThat(source.sinceCharge()).isNull()
        assertThat(shell.asked).isEqualTo(1)

        now += 30_000L
        val report = source.sinceCharge()
        assertThat(report).isNotNull()
        now += 59_000L
        assertThat(source.sinceCharge()).isSameInstanceAs(report)
        assertThat(shell.asked).isEqualTo(2)
    }

    @Test
    fun `while access is off the shell is never asked`() {
        val shell = ScriptedShell(dump)

        assertThat(source(shell, FakeAppSettingsRepository()).sinceCharge()).isNull()
        assertThat(shell.asked).isEqualTo(0)
    }

    @Test
    fun `switching the route forgets what the other shell answered`() {
        val shell = ScriptedShell(answer = null)
        val settings = FakeAppSettingsRepository(access = PrivilegedAccess.SHIZUKU)
        val source = source(shell, settings)
        assertThat(source.sinceCharge()).isNull()

        shell.answer = dump
        settings.setPrivilegedAccess(PrivilegedAccess.ROOT)

        // Not a minute later, yet asked again: the failure was the other shell's.
        assertThat(source.sinceCharge()).isNotNull()
        assertThat(shell.asked).isEqualTo(2)
    }
}
