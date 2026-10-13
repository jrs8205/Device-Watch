package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test
import java.time.ZoneId

class ShellPrivacyLogSourceTest {

    /** Answers with [answer] and remembers what it was asked. */
    private class ScriptedShell(var answer: String?) : PrivilegedShell {
        val commands = ArrayList<String>()
        override fun run(command: String, timeoutMillis: Long): String? {
            commands += command
            return answer
        }
        override fun close() = Unit
    }

    private val table = """
        ------------Discrete App Ops-------------

        UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
        10171|com.example.cam|default:0|CAMERA|null|2026-10-12 09:36:03.307|1|0|0|top|s|-1|0
    """.trimIndent()

    private var screenOn = true
    private var now = 1_000L

    private fun source(
        shell: PrivilegedShell,
        settings: AppSettingsRepository = FakeAppSettingsRepository(access = PrivilegedAccess.SHIZUKU),
        sdk: Int = 37,
    ) = ShellPrivacyLogSource(
        settings, shell,
        screenOn = { screenOn },
        now = { now },
        zone = { ZoneId.of("Europe/Helsinki") },
        label = { uid, pkg -> "$pkg/$uid" },
        sdk = sdk,
    )

    @Test
    fun `with access off nothing is asked and nothing answered`() {
        val shell = ScriptedShell(table)
        val source = source(shell, FakeAppSettingsRepository(access = PrivilegedAccess.OFF))
        assertThat(source.read()).isNull()
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun `the record is asked for with one command and built with the labels given`() {
        val shell = ScriptedShell(table)
        val read = source(shell).read()!!
        assertThat(shell.commands).containsExactly("dumpsys appops --history --include-discrete 0")
        assertThat(read.unsupported).isFalse()
        val app = read.log!!.sensor(PrivacySensor.CAMERA).apps.single()
        assertThat(app.label).isEqualTo("com.example.cam/10171")
        assertThat(app.accesses).isEqualTo(1)
    }

    @Test
    fun `on Android 11 and older nothing is asked, and the answer says this Android keeps no record`() {
        val shell = ScriptedShell(table)
        val read = source(shell, sdk = 30).read()!!
        assertThat(read.unsupported).isTrue()
        assertThat(read.log).isNull()
        assertThat(shell.commands).isEmpty()
        assertThat(source(ScriptedShell(table), sdk = 31).read()!!.unsupported).isFalse()
    }

    @Test
    fun `a dump this Android should have but the parser cannot read is a failed read, not an empty record`() {
        val shell = ScriptedShell("Current AppOps Service state:\n  Settings:\n")
        val source = source(shell)
        assertThat(source.read()).isNull()
        assertThat(shell.commands).hasSize(1)
        // And it is not asked again within the interval, like any failed read.
        now += 5_000L
        assertThat(source.read()).isNull()
        assertThat(shell.commands).hasSize(1)
    }

    @Test
    fun `a shell that does not answer reads as nothing, and is not asked again for a while`() {
        val shell = ScriptedShell(null)
        val source = source(shell)
        assertThat(source.read()).isNull()
        assertThat(shell.commands).hasSize(1)
        shell.answer = table
        now += 5_000L
        assertThat(source.read()).isNull()
        assertThat(shell.commands).hasSize(1)
        now += ShellPrivacyLogSource.MIN_INTERVAL_MILLIS
        assertThat(source.read()).isNotNull()
        assertThat(shell.commands).hasSize(2)
    }

    @Test
    fun `within the interval the last read stands`() {
        val shell = ScriptedShell(table)
        val source = source(shell)
        val first = source.read()
        now += 10_000L
        assertThat(source.read()).isSameInstanceAs(first)
        assertThat(shell.commands).hasSize(1)
        now += ShellPrivacyLogSource.MIN_INTERVAL_MILLIS
        source.read()
        assertThat(shell.commands).hasSize(2)
    }

    @Test
    fun `with the screen off no dump is asked for, and the last one stands`() {
        val shell = ScriptedShell(table)
        val source = source(shell)
        screenOn = false
        assertThat(source.read()).isNull()
        assertThat(shell.commands).isEmpty()
        screenOn = true
        val read = source.read()
        assertThat(read).isNotNull()
        screenOn = false
        now += 10 * 60_000L
        assertThat(source.read()).isSameInstanceAs(read)
        assertThat(shell.commands).hasSize(1)
    }

    @Test
    fun `switching the route forgets the other shell's answer`() {
        val shell = ScriptedShell(table)
        val settings = FakeAppSettingsRepository(access = PrivilegedAccess.SHIZUKU)
        val source = source(shell, settings)
        assertThat(source.read()).isNotNull()
        settings.setPrivilegedAccess(PrivilegedAccess.OFF)
        assertThat(source.read()).isNull()
        settings.setPrivilegedAccess(PrivilegedAccess.ROOT)
        // A fresh dump at once, interval or not: this is another shell.
        assertThat(source.read()).isNotNull()
        assertThat(shell.commands).hasSize(2)
    }
}
