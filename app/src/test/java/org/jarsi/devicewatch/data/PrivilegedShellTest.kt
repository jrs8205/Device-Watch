package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class PrivilegedShellTest {

    private class RecordingRoot : RootShell {
        val commands = mutableListOf<String>()
        var closed = 0
        override val lost = MutableStateFlow(false)
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

    /** Answers until told to fail, and counts what it was asked. */
    private class ScriptedShell(var answer: String?) : PrivilegedShell {
        var asked = 0
        override fun run(command: String, timeoutMillis: Long): String? {
            asked++
            return answer
        }
        override fun close() = Unit
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
    fun `a shell that fails once is not asked again in the same poll`() {
        // Each further command would wait out its own timeout, and a poll reads twenty things.
        val shell = ScriptedShell(answer = "ok")
        val pass = ShellPass(shell, usable = true)

        assertThat(pass.run("one")).isEqualTo("ok")
        shell.answer = null
        assertThat(pass.run("two")).isNull()
        shell.answer = "ok"
        assertThat(pass.run("three")).isNull()

        assertThat(shell.asked).isEqualTo(2)
        // The next poll starts afresh.
        assertThat(ShellPass(shell, usable = true).run("four")).isEqualTo("ok")
    }

    @Test
    fun `an empty answer is an answer, and a poll without the shell asks nothing`() {
        val shell = ScriptedShell(answer = "")
        val pass = ShellPass(shell, usable = true)

        assertThat(pass.run("cat /nonexistent")).isEmpty()
        assertThat(pass.run("echo")).isEmpty()
        assertThat(pass.usable).isTrue()

        assertThat(ShellPass(shell, usable = false).run("id")).isNull()
        assertThat(shell.asked).isEqualTo(2)
    }

    @Test
    fun `a phone without su answers that it is not rooted`() = runTest {
        assertThat(SuRootShell("/nonexistent/su", System::nanoTime).requestAccess()).isEqualTo(RootAccess.NO_SU)
    }

    @Test
    fun `a shell that is not root counts as a refusal and leaves nothing running`() = runTest {
        // A plain sh answers `id -u` with the test user's id, as a su that was refused never does.
        val sh = TestShell.path
        assumeNotNull(sh)
        assumeFalse(System.getProperty("user.name") == "root")
        val shell = SuRootShell(sh!!, System::nanoTime)

        assertThat(shell.requestAccess()).isEqualTo(RootAccess.DENIED)
        // Nothing was kept, so the next command has to reopen and is refused again.
        assertThat(shell.run("echo hi")).isNull()
    }

    @Test
    fun `root that stays refused is given up on instead of being asked for ever`() {
        // Each attempt makes the root manager prompt, or announce a denial.
        var now = 1L
        val shell = SuRootShell("/nonexistent/su") { now }

        assertThat(shell.run("id")).isNull()
        assertThat(shell.lost.value).isFalse()
        // Asked again too soon: no new attempt, so the count does not move.
        now += TimeUnit.MINUTES.toNanos(1)
        assertThat(shell.run("id")).isNull()
        now += TimeUnit.MINUTES.toNanos(5)
        assertThat(shell.run("id")).isNull()
        assertThat(shell.lost.value).isFalse()
        now += TimeUnit.MINUTES.toNanos(6)
        assertThat(shell.run("id")).isNull()

        assertThat(shell.lost.value).isTrue()
    }
}
