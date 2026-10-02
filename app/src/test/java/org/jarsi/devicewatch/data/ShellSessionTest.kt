package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * The conversation with the shell, driven through a plain `sh`: the framing is the
 * same whoever runs the commands, and neither root nor Shizuku can be had in a unit test.
 */
class ShellSessionTest {

    private fun openSh(): ShellSession {
        val sh = TestShell.path
        assumeNotNull(sh)
        return ShellSession.open(sh!!).also(TestShell::prepare)
    }

    @Test
    fun `a line ending in the marker ends the command and keeps what precedes it`() {
        assertThat(ShellProtocol.beforeMarker("__m__", "__m__")).isEmpty()
        assertThat(ShellProtocol.beforeMarker("42__m__", "__m__")).isEqualTo("42")
        assertThat(ShellProtocol.beforeMarker("42", "__m__")).isNull()
    }

    @Test
    fun `markers differ from command to command`() {
        assertThat(ShellProtocol.newMarker()).isNotEqualTo(ShellProtocol.newMarker())
    }

    @Test
    fun `quoting survives a single quote in the value`() {
        assertThat(ShellProtocol.quote("/a b/it's")).isEqualTo("'/a b/it'\\''s'")
    }

    @Test
    fun `output comes back line by line without the marker`() {
        val session = openSh()
        try {
            assertThat(session.exec("echo one; echo two", 5_000L)).isEqualTo("one\ntwo")
        } finally {
            session.close()
        }
    }

    @Test
    fun `output without a trailing newline is kept whole`() {
        val session = openSh()
        try {
            assertThat(session.exec("printf 37000", 5_000L)).isEqualTo("37000")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a failing command answers empty and the session carries on`() {
        val session = openSh()
        try {
            assertThat(session.exec("cat /nonexistent/file", 5_000L)).isEmpty()
            assertThat(session.exec("for i in 1 2; do echo \$i; done", 5_000L)).isEqualTo("1\n2")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a command that reads its input cannot swallow the next one`() {
        val session = openSh()
        try {
            assertThat(session.exec("cat", 5_000L)).isEmpty()
            assertThat(session.exec("echo next", 5_000L)).isEqualTo("next")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a command that outlasts its timeout answers null`() {
        val session = openSh()
        try {
            assertThat(session.exec("sleep 5", 200L)).isNull()
        } finally {
            session.close()
        }
    }

    @Test
    fun `a shell that exits answers null`() {
        val session = openSh()
        try {
            assertThat(session.exec("exit 1", 5_000L)).isNull()
        } finally {
            session.close()
        }
    }

    @Test
    fun `closing from another thread ends the command in flight`() {
        val session = openSh()
        val started = System.nanoTime()
        Thread {
            Thread.sleep(200L)
            session.close()
        }.start()

        assertThat(session.exec("sleep 20", 15_000L)).isNull()
        assertThat((System.nanoTime() - started) / 1_000_000L).isLessThan(10_000L)
        assertThat(session.isOpen).isFalse()
    }

    @Test
    fun `a session speaks over any pair of streams`() {
        // What a Shizuku shell hands over is two pipes, not a process.
        val sh = TestShell.path
        assumeNotNull(sh)
        val process = ProcessBuilder(sh).redirectErrorStream(true).start()
        val session = ShellSession(process.inputStream, process.outputStream, process::destroy)
        TestShell.prepare(session)
        try {
            assertThat(session.exec("echo piped", 5_000L)).isEqualTo("piped")
        } finally {
            session.close()
        }
    }
}

/** A POSIX shell for the tests: on the PATH everywhere but Windows, where Git ships one. */
internal object TestShell {
    /** Git's sh, started outside its own terminal, does not have its tools (cat, sleep) on the PATH. */
    fun prepare(session: ShellSession) {
        if (File.separatorChar == '\\') session.exec("export PATH=\"/usr/bin:\$PATH\"", 5_000L)
    }

    val path: String? by lazy {
        val candidates = listOf(
            "sh",
            "C:\\Program Files\\Git\\usr\\bin\\sh.exe",
            "C:\\Program Files\\Git\\bin\\sh.exe",
        )
        candidates.firstOrNull { candidate ->
            if (candidate.contains(File.separatorChar) && !File(candidate).exists()) return@firstOrNull false
            try {
                ProcessBuilder(candidate, "-c", "exit 0").start().waitFor() == 0
            } catch (_: IOException) {
                false
            }
        }
    }
}
