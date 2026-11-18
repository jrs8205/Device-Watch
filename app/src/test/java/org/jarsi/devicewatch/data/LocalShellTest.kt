package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The app's own shell, with a stand-in for `sh` at the far end of the session's
 * pipes: the framing is the real one, and no process is started.
 */
class LocalShellTest {

    private class Grants(var missing: Set<AdbPermission> = emptySet()) : AdbGrants {
        override fun missing(): Set<AdbPermission> = missing
    }

    /** Answers each framed command from [answer]; a null answer is a command that never returns. */
    private class FakeSh(private val answer: (String) -> String?) {
        private val toShell = PipedOutputStream()
        private val shellInput = PipedInputStream(toShell)
        private val fromShell = PipedOutputStream()
        private val sessionInput = PipedInputStream(fromShell)
        val commands = CopyOnWriteArrayList<String>()
        val session = ShellSession(sessionInput, toShell)

        init {
            thread(isDaemon = true, name = "fake-sh") {
                val writer = fromShell.bufferedWriter()
                var command = ""
                try {
                    shellInput.bufferedReader().forEachLine { line ->
                        when {
                            line.startsWith("{ ") -> command = line.removePrefix("{ ")
                            line.startsWith("echo __dw_") -> {
                                commands += command
                                val output = answer(command) ?: return@forEachLine
                                if (output.isNotEmpty()) writer.write(output + "\n")
                                writer.write(line.removePrefix("echo ") + "\n")
                                writer.flush()
                            }
                        }
                    }
                } catch (_: IOException) {
                    // The session was closed.
                }
            }
        }
    }

    private class Opener(private val answer: (String) -> String?) : () -> ShellSession {
        val shells = mutableListOf<FakeSh>()
        override fun invoke(): ShellSession = FakeSh(answer).also { shells += it }.session
    }

    @Test
    fun `without the DUMP grant nothing is started and the grant is reported lost`() {
        val opener = Opener { "never" }
        val shell = ShLocalShell(Grants(missing = setOf(AdbPermission.DUMP)), opener)

        assertThat(shell.run("dumpsys thermalservice")).isNull()

        assertThat(opener.shells).isEmpty()
        assertThat(shell.lost.value).isTrue()
    }

    @Test
    fun `any one of the three grants missing stops the shell, as it stops the switch`() {
        // The route is on only with all three; with one gone it goes off, whichever it is.
        for (permission in AdbPermission.entries) {
            val opener = Opener { "never" }
            val shell = ShLocalShell(Grants(missing = setOf(permission)), opener)

            assertThat(shell.run("dumpsys thermalservice")).isNull()

            assertThat(opener.shells).isEmpty()
            assertThat(shell.lost.value).isTrue()
        }
    }

    @Test
    fun `a grant taken away while the shell is open ends the shell, with no screen there to close it`() {
        // Only the monitor may be running: nothing else would end the process.
        val grants = Grants()
        val opener = Opener { "ok" }
        val shell = ShLocalShell(grants, opener)
        shell.run("one")
        val generation = shell.generation

        grants.missing = setOf(AdbPermission.BATTERY_STATS)

        assertThat(shell.run("two")).isNull()
        assertThat(shell.lost.value).isTrue()
        assertThat(opener.shells.single().session.isOpen).isFalse()
        assertThat(opener.shells.single().commands).containsExactly("one")
        // The loss is not a close: a command already routed still matches, and finds the grant gone.
        assertThat(shell.generation).isEqualTo(generation)
    }

    @Test
    fun `commands run in one shell that the next command uses again`() {
        val opener = Opener { command -> "ran $command" }
        val shell = ShLocalShell(Grants(), opener)

        assertThat(shell.run("one")).isEqualTo("ran one")
        assertThat(shell.run("two")).isEqualTo("ran two")

        assertThat(opener.shells).hasSize(1)
        assertThat(opener.shells.single().commands).containsExactly("one", "two").inOrder()
    }

    @Test
    fun `a command decided on before the shell was closed starts nothing`() {
        val opener = Opener { "ok" }
        val shell = ShLocalShell(Grants(), opener)
        val wanted = shell.generation

        shell.close()

        assertThat(shell.run("id", 1_000L, wanted)).isNull()
        assertThat(opener.shells).isEmpty()
    }

    @Test
    fun `closing ends the shell and the next command starts a new one`() {
        val opener = Opener { "ok" }
        val shell = ShLocalShell(Grants(), opener)
        shell.run("one")

        shell.close()

        assertThat(opener.shells.single().session.isOpen).isFalse()
        assertThat(shell.run("two")).isEqualTo("ok")
        assertThat(opener.shells).hasSize(2)
    }

    @Test
    fun `a refused dump with the grant still held answers empty and the route stays`() {
        val opener = Opener { "Permission Denial: can't dump batterystats due to android:get_usage_stats app-op not allowed" }
        val shell = ShLocalShell(Grants(), opener)

        assertThat(shell.run("dumpsys batterystats --charged")).isEmpty()

        assertThat(shell.lost.value).isFalse()
    }

    @Test
    fun `a refused dump after a grant was taken away reports it lost and ends the shell`() {
        val grants = Grants()
        val opener = Opener {
            // Revoked between the check and Android's own.
            grants.missing = setOf(AdbPermission.PACKAGE_USAGE_STATS)
            "Permission Denial: can't dump batterystats due to missing android.permission.PACKAGE_USAGE_STATS permission"
        }
        val shell = ShLocalShell(grants, opener)

        assertThat(shell.run("dumpsys batterystats --charged")).isNull()

        assertThat(shell.lost.value).isTrue()
        assertThat(opener.shells.single().session.isOpen).isFalse()
    }

    @Test
    fun `closing clears a lost grant so that switching on again starts afresh`() {
        val grants = Grants(missing = setOf(AdbPermission.DUMP))
        val shell = ShLocalShell(grants, Opener { "ok" })
        shell.run("id")
        assertThat(shell.lost.value).isTrue()

        shell.close()
        grants.missing = emptySet()

        assertThat(shell.lost.value).isFalse()
        assertThat(shell.run("id")).isEqualTo("ok")
    }

    @Test
    fun `a shell that cannot be started answers nothing and loses nothing`() {
        val shell = ShLocalShell(Grants()) { throw IOException("no sh") }

        assertThat(shell.run("id")).isNull()

        assertThat(shell.lost.value).isFalse()
    }

    @Test
    fun `a command left unanswered ends that shell, and the next command starts a new one`() {
        var answers = false
        val opener = Opener { if (answers) "ok" else null }
        val shell = ShLocalShell(Grants(), opener)

        assertThat(shell.run("hangs", 100L)).isNull()
        assertThat(opener.shells.single().session.isOpen).isFalse()

        answers = true
        assertThat(shell.run("id")).isEqualTo("ok")
        assertThat(opener.shells).hasSize(2)
    }
}
