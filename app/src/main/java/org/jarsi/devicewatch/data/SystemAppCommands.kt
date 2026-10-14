package org.jarsi.devicewatch.data

/** How a removal or a restore went. */
sealed interface SystemAppActionResult {
    data object Done : SystemAppActionResult

    /** Extended access was off, or the shell did not answer. */
    data object NoShell : SystemAppActionResult

    /** The package manager refused, in its own words (which may be none). */
    data class Failed(val message: String) : SystemAppActionResult
}

/**
 * The two package-manager commands behind the page. A package name that is not
 * one never reaches the shell, and the name is quoted besides: nothing read from
 * the phone can change what a command does.
 */
internal object SystemAppCommands {

    /** Java package syntax, which is what Android accepts as a package name. */
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

    /**
     * Removes the app for one user: the system image keeps its copy, so
     * [restore] can bring it back, and so can a factory reset.
     */
    fun uninstall(packageName: String, userId: Int): String =
        "pm uninstall --user $userId ${argument(packageName, userId)}"

    fun restore(packageName: String, userId: Int): String =
        "pm install-existing --user $userId ${argument(packageName, userId)}"

    /** The package manager's record of one package, user by user. */
    fun dumpPackage(packageName: String, userId: Int): String =
        "dumpsys package ${argument(packageName, userId)}"

    /** `UNINSTALL_REASON_USER_TYPE`: kept out of a user by the kind of user it is, never removed by anyone. */
    private const val UNINSTALL_REASON_USER_TYPE = 1

    /**
     * Whether [dump] says the package is out of user [userId] by design, as a
     * private space's own apps are out of the main user: nothing was removed, so
     * there is nothing to restore. Unknown (no dump, no such line) counts as no.
     */
    fun neverInstalledForUser(dump: String?, userId: Int): Boolean {
        if (dump == null) return false
        val reason = Regex("""^\s*User $userId:.*\n(?:\s+\S.*\n)*?\s+uninstallReason=(\d+)""", RegexOption.MULTILINE)
            .find(dump)?.groupValues?.get(1)?.toIntOrNull()
        return reason == UNINSTALL_REASON_USER_TYPE
    }

    /**
     * What the package manager's answer came to. `pm uninstall` says `Success`,
     * `pm install-existing` names the package and the user; anything else is its
     * reason for refusing.
     */
    fun outcome(output: String?): SystemAppActionResult {
        if (output == null) return SystemAppActionResult.NoShell
        val answer = output.trim()
        return if (answer.startsWith("Success") || answer.contains("installed for user")) {
            SystemAppActionResult.Done
        } else {
            SystemAppActionResult.Failed(answer)
        }
    }

    private fun argument(packageName: String, userId: Int): String {
        require(PACKAGE_NAME.matches(packageName)) { "not a package name" }
        require(userId >= 0) { "not a user" }
        return ShellProtocol.quote(packageName)
    }
}
