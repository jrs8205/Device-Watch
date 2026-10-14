package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemAppCommandsTest {

    @Test
    fun `a removal is for this user only, the package quoted`() {
        assertThat(SystemAppCommands.uninstall("com.example.bloat", userId = 0)).isEqualTo("pm uninstall --user 0 'com.example.bloat'")
        assertThat(SystemAppCommands.uninstall("com.example.bloat", userId = 10)).isEqualTo("pm uninstall --user 10 'com.example.bloat'")
        assertThat(SystemAppCommands.restore("com.example.bloat", userId = 0)).isEqualTo("pm install-existing --user 0 'com.example.bloat'")
    }

    @Test
    fun `a package name that is not one is refused before it reaches the shell`() {
        assertThrows(IllegalArgumentException::class.java) { SystemAppCommands.uninstall("", userId = 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemAppCommands.uninstall("com.example; reboot", userId = 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemAppCommands.uninstall("com.example'", userId = 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemAppCommands.restore("com.example bloat", userId = 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemAppCommands.uninstall("com.example", userId = -1) }
    }

    @Test
    fun `a package's user state is asked of the package manager's dump`() {
        assertThat(SystemAppCommands.dumpPackage("com.android.privatespace", userId = 0)).isEqualTo("dumpsys package 'com.android.privatespace'")
    }

    @Test
    fun `the dump says whether a package was ever installed for this user`() {
        val dump = """
            Packages:
              Package [com.android.privatespace] (a1b2c3):
                userId=10049
                User 0: ceDataInode=0 deDataInode=0 installed=false hidden=false suspended=false distractionFlags=0 stopped=true notLaunched=false enabled=0 instant=false virtual=false
                  installReason=0
                  dataDir=null
                  firstInstallTime=2026-10-11 21:22:18
                  uninstallReason=1
                User 10: ceDataInode=123 deDataInode=456 installed=true hidden=false suspended=false distractionFlags=0 stopped=false notLaunched=false enabled=0 instant=false virtual=false
                  installReason=0
                  uninstallReason=0
        """.trimIndent()
        // Kept out of user 0 by its kind (a private-space app): never installed there, nothing to restore.
        assertThat(SystemAppCommands.neverInstalledForUser(dump, userId = 0)).isTrue()
        assertThat(SystemAppCommands.neverInstalledForUser(dump, userId = 10)).isFalse()
        // A user's removal leaves reason 0.
        val removed = dump.replace("uninstallReason=1", "uninstallReason=0")
        assertThat(SystemAppCommands.neverInstalledForUser(removed, userId = 0)).isFalse()
        // No dump, or a dump without the line: not known to be by design, so it stays on the list.
        assertThat(SystemAppCommands.neverInstalledForUser(null, userId = 0)).isFalse()
        assertThat(SystemAppCommands.neverInstalledForUser("", userId = 0)).isFalse()
        assertThat(SystemAppCommands.neverInstalledForUser(dump, userId = 11)).isFalse()
    }

    @Test
    fun `the package manager's answer is read for success, and its words kept on failure`() {
        assertThat(SystemAppCommands.outcome(null)).isEqualTo(SystemAppActionResult.NoShell)
        assertThat(SystemAppCommands.outcome("Success")).isEqualTo(SystemAppActionResult.Done)
        assertThat(SystemAppCommands.outcome("Success\n")).isEqualTo(SystemAppActionResult.Done)
        assertThat(SystemAppCommands.outcome("Package com.example.bloat installed for user: 0")).isEqualTo(SystemAppActionResult.Done)
        assertThat(SystemAppCommands.outcome("Failure [DELETE_FAILED_INTERNAL_ERROR]"))
            .isEqualTo(SystemAppActionResult.Failed("Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertThat(SystemAppCommands.outcome("Error: Package com.example.bloat doesn't exist\n"))
            .isEqualTo(SystemAppActionResult.Failed("Error: Package com.example.bloat doesn't exist"))
        assertThat(SystemAppCommands.outcome("")).isEqualTo(SystemAppActionResult.Failed(""))
    }
}
