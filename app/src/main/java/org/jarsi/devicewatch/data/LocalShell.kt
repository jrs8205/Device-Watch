package org.jarsi.devicewatch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A shell run as the app itself; used only while [PrivilegedAccess.ADB] is
 * selected. It has no rights of its own: what its commands may read is what the
 * [AdbPermission]s granted from a computer let the app's own user read.
 */
interface LocalShell : PrivilegedBackend {
    /**
     * True once a command found one of the [AdbPermission]s no longer granted:
     * revoked from a computer. The shell has ended by then. Cleared by [close].
     */
    val lost: StateFlow<Boolean>
}

/** Whether a service answered a dump request with a refusal instead of its dump. */
internal object DumpDenial {
    /**
     * A service that refuses writes "Permission Denial: …" as the dump itself, so
     * the refusal arrives as output. Only the first line counts: further down the
     * words may be something an app logged.
     */
    fun isDenied(output: String): Boolean =
        output.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart()?.startsWith("Permission Denial") == true
}

/** One long-lived `sh` fed commands over stdin ([ShellSession]). */
@Singleton
class ShLocalShell internal constructor(
    private val grants: AdbGrants,
    private val open: () -> ShellSession,
) : LocalShell {

    @Inject
    constructor(grants: AdbGrants) : this(grants, { ShellSession.open("sh") })

    private val _lost = MutableStateFlow(false)
    override val lost: StateFlow<Boolean> = _lost

    /** Serializes commands; never held by [close], which must not wait for one. */
    private val commandLock = Any()

    /** Guards the fields below for the instant a session changes hands; nothing blocks under it. */
    private val stateLock = Any()
    private var session: ShellSession? = null

    /** Written under [stateLock]; read anywhere. */
    @Volatile
    override var generation = 0
        private set

    override fun run(command: String, timeoutMillis: Long): String? = run(command, timeoutMillis, generation)

    override fun run(command: String, timeoutMillis: Long, wanted: Int): String? {
        synchronized(commandLock) {
            // Switched off since this command was decided on, or while it waited
            // its turn: it must not bring the shell back.
            if (synchronized(stateLock) { generation } != wanted) return null
            // Before anything is started. The route is on only with all three
            // granted, so one gone takes it off, whichever it is: without DUMP
            // every dump is refused, and the other two would leave their pages
            // empty under a switch that says on.
            if (grantGone()) return null
            val current = synchronized(stateLock) { session?.takeIf { it.isOpen } } ?: reopen(wanted) ?: return null
            val output = current.exec(command, timeoutMillis)
            if (output == null) {
                // An unanswered command leaves the stream in an unknown state, so the shell goes.
                current.close()
                synchronized(stateLock) { if (session === current) session = null }
                return null
            }
            // Refused with the grants still held is some other check of Android's
            // (usage access switched off in Settings): the command ran and gave nothing.
            if (DumpDenial.isDenied(output)) return if (grantGone()) null else ""
            return output
        }
    }

    /** Not under [commandLock]: ending the process is what ends a command in flight. */
    override fun close() {
        synchronized(stateLock) {
            generation++
            session?.close()
            session = null
        }
        _lost.value = false
    }

    /**
     * Whether a grant is gone, and if so the end of the shell: the setting goes
     * off where the caller sees [lost], possibly with no screen to close
     * anything, and a route that is off leaves no process running. The
     * generation stays as it is; moving it on is [close]'s.
     */
    private fun grantGone(): Boolean {
        if (grants.missing().isEmpty()) return false
        _lost.value = true
        synchronized(stateLock) {
            session?.close()
            session = null
        }
        return true
    }

    private fun reopen(wanted: Int): ShellSession? {
        val opened = try {
            open()
        } catch (_: IOException) {
            return null
        }
        synchronized(stateLock) {
            if (generation == wanted) {
                session = opened
                return opened
            }
        }
        // Closed while it was starting: off wins.
        opened.close()
        return null
    }
}
