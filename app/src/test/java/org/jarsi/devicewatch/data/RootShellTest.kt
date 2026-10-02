package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The conversation with the shell, driven through a plain `sh`: the framing is the
 * same whoever runs the commands, and root itself cannot be had in a unit test.
 */
class RootShellTest {

    @Test
    fun `a line ending in the marker ends the command and keeps what precedes it`() {
        assertThat(RootShellProtocol.beforeMarker("__m__", "__m__")).isEmpty()
        assertThat(RootShellProtocol.beforeMarker("42__m__", "__m__")).isEqualTo("42")
        assertThat(RootShellProtocol.beforeMarker("42", "__m__")).isNull()
    }

    @Test
    fun `markers differ from command to command`() {
        assertThat(RootShellProtocol.newMarker()).isNotEqualTo(RootShellProtocol.newMarker())
    }

    @Test
    fun `quoting survives a single quote in the value`() {
        assertThat(RootShellProtocol.quote("/a b/it's")).isEqualTo("'/a b/it'\\''s'")
    }

    @Test
    fun `output comes back line by line without the marker`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("echo one; echo two", 5_000L)).isEqualTo("one\ntwo")
        } finally {
            session.close()
        }
    }

    @Test
    fun `output without a trailing newline is kept whole`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("printf 37000", 5_000L)).isEqualTo("37000")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a failing command answers empty and the session carries on`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("cat /nonexistent/file", 5_000L)).isEmpty()
            assertThat(session.exec("for i in 1 2; do echo \$i; done", 5_000L)).isEqualTo("1\n2")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a command that reads its input cannot swallow the next one`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("cat", 5_000L)).isEmpty()
            assertThat(session.exec("echo next", 5_000L)).isEqualTo("next")
        } finally {
            session.close()
        }
    }

    @Test
    fun `a command that outlasts its timeout answers null`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("sleep 5", 200L)).isNull()
        } finally {
            session.close()
        }
    }

    @Test
    fun `a shell that exits answers null`() {
        val session = ShellSession.open("sh")
        try {
            assertThat(session.exec("exit 1", 5_000L)).isNull()
        } finally {
            session.close()
        }
    }
}
