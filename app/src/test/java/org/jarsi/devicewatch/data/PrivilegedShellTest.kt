package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNotNull
import org.junit.Test

class PrivilegedShellTest {

    private class RecordingRoot : RootShell {
        val commands = mutableListOf<String>()
        var closed = 0
        override fun run(command: String, timeoutMillis: Long): String? {
            commands += command
            return "root"
        }
        override suspend fun requestAccess() = RootAccess.GRANTED
        override fun close() {
            closed++
        }
    }

    private class RecordingShizuku : ShizukuShell {
        val commands = mutableListOf<String>()
        var closed = 0
        override val alive = MutableStateFlow(true)
        override fun run(command: String, timeoutMillis: Long): String? {
            commands += command
            return "shizuku"
        }
        override suspend fun requestAccess() = ShizukuAccess.GRANTED
        override fun close() {
            closed++
        }
    }

    @Test
    fun `while access is off no backend is ever asked`() {
        val root = RecordingRoot()
        val shizuku = RecordingShizuku()
        val shell = SelectedPrivilegedShell(FakeAppSettingsRepository(), root, shizuku)

        assertThat(shell.run("id")).isNull()
        assertThat(root.commands).isEmpty()
        assertThat(shizuku.commands).isEmpty()
    }

    @Test
    fun `a command goes to the backend the setting names`() {
        val root = RecordingRoot()
        val shizuku = RecordingShizuku()
        val settings = FakeAppSettingsRepository()
        val shell = SelectedPrivilegedShell(settings, root, shizuku)

        settings.setPrivilegedAccess(PrivilegedAccess.SHIZUKU)
        assertThat(shell.run("id")).isEqualTo("shizuku")

        settings.setPrivilegedAccess(PrivilegedAccess.ROOT)
        assertThat(shell.run("id")).isEqualTo("root")

        assertThat(shizuku.commands).containsExactly("id")
        assertThat(root.commands).containsExactly("id")
    }

    @Test
    fun `closing ends both backends`() {
        val root = RecordingRoot()
        val shizuku = RecordingShizuku()

        SelectedPrivilegedShell(FakeAppSettingsRepository(), root, shizuku).close()

        assertThat(root.closed).isEqualTo(1)
        assertThat(shizuku.closed).isEqualTo(1)
    }

    @Test
    fun `a phone without su answers that it is not rooted`() = runTest {
        assertThat(SuRootShell("/nonexistent/su").requestAccess()).isEqualTo(RootAccess.NO_SU)
    }

    @Test
    fun `a shell that is not root counts as a refusal and leaves nothing running`() = runTest {
        // A plain sh answers `id -u` with the test user's id, as a su that was refused never does.
        val sh = TestShell.path
        assumeNotNull(sh)
        assumeFalse(System.getProperty("user.name") == "root")
        val shell = SuRootShell(sh!!)

        assertThat(shell.requestAccess()).isEqualTo(RootAccess.DENIED)
        // Nothing was kept, so the next command has to reopen and is refused again.
        assertThat(shell.run("echo hi")).isNull()
    }
}
