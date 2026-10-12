package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

class RootOnlyShellTest {

    private class RecordingRoot : RootShell {
        val commands = mutableListOf<Pair<String, Long>>()
        var loseRootOnNextCommand = false
        override var generation = 0
        override val lost = MutableStateFlow(false)
        override fun run(command: String, timeoutMillis: Long): String? = run(command, timeoutMillis, generation)
        override fun run(command: String, timeoutMillis: Long, wanted: Int): String? {
            if (wanted != generation) return null
            commands += command to timeoutMillis
            if (loseRootOnNextCommand) {
                lost.value = true
                return null
            }
            return "out"
        }
        override suspend fun requestAccess() = RootAccess.GRANTED
        override fun close() {
            generation++
        }
    }

    /** The user switches root off at the very moment the gate reads the setting. */
    private class SwitchedOffWhenRead(
        private val delegate: AppSettingsRepository,
        private val root: RootShell,
    ) : AppSettingsRepository by delegate {
        override fun privilegedAccess(): PrivilegedAccess {
            val read = delegate.privilegedAccess()
            delegate.setPrivilegedAccess(PrivilegedAccess.OFF)
            root.close()
            return read
        }
    }

    private val root = RecordingRoot()

    @Test
    fun `nothing runs while access is off`() {
        val gate = RootOnlyShell(FakeAppSettingsRepository(access = PrivilegedAccess.OFF), root) { true }
        assertThat(gate.run("echo")).isNull()
        assertThat(root.commands).isEmpty()
    }

    @Test
    fun `nothing runs through shizuku either`() {
        val gate = RootOnlyShell(FakeAppSettingsRepository(access = PrivilegedAccess.SHIZUKU), root) { true }
        assertThat(gate.run("echo")).isNull()
        assertThat(root.commands).isEmpty()
    }

    @Test
    fun `with root on the command reaches the root shell with its timeout`() {
        val gate = RootOnlyShell(FakeAppSettingsRepository(access = PrivilegedAccess.ROOT), root) { true }
        assertThat(gate.run("echo", 5_000L)).isEqualTo("out")
        assertThat(root.commands).containsExactly("echo" to 5_000L)
    }

    @Test
    fun `a command decided on as root went off reaches nothing`() {
        val settings = SwitchedOffWhenRead(FakeAppSettingsRepository(access = PrivilegedAccess.ROOT), root)
        val gate = RootOnlyShell(settings, root) { true }
        assertThat(gate.run("echo")).isNull()
        assertThat(root.commands).isEmpty()
    }

    @Test
    fun `nothing runs with the screen off`() {
        val gate = RootOnlyShell(FakeAppSettingsRepository(access = PrivilegedAccess.ROOT), root) { false }
        assertThat(gate.run("echo")).isNull()
        assertThat(root.commands).isEmpty()
    }

    @Test
    fun `root found gone switches the setting off`() {
        val settings = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT)
        root.loseRootOnNextCommand = true
        val gate = RootOnlyShell(settings, root) { true }
        assertThat(gate.run("echo")).isNull()
        assertThat(settings.privilegedAccess()).isEqualTo(PrivilegedAccess.OFF)
    }
}
