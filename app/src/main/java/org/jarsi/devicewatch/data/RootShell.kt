package org.jarsi.devicewatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a request for root ended. */
enum class RootAccess { GRANTED, DENIED, NO_SU }

/** A root shell on a rooted phone; used only while [PrivilegedAccess.ROOT] is selected. */
interface RootShell : PrivilegedBackend {
    /**
     * True once root that had been granted turned out to be gone: revoked, a
     * timed grant run out, the root manager removed. The shell then stops asking
     * until [requestAccess] is called again, so the root manager does not keep
     * prompting, or announcing refusals, behind the user's back.
     */
    val lost: StateFlow<Boolean>

    /**
     * Opens a root shell, which is what makes the root manager ask the user. The
     * caller selects root access only after GRANTED.
     */
    suspend fun requestAccess(): RootAccess
}

/** One long-lived `su` process fed commands over stdin ([ShellSession]). */
@Singleton
class SuRootShell internal constructor(
    private val binary: String,
    private val nanoTime: () -> Long,
) : RootShell {

    @Inject
    constructor() : this("su", System::nanoTime)

    private val _lost = MutableStateFlow(false)
    override val lost: StateFlow<Boolean> = _lost

    /** Serializes commands; never held by [close], which must not wait for one. */
    private val commandLock = Any()

    /** Guards the fields below for the instant a session changes hands; nothing blocks under it. */
    private val stateLock = Any()
    private var session: ShellSession? = null

    /** A shell still waiting for the root manager's answer. */
    private var opening: ShellSession? = null

    /** Written under [stateLock]; read anywhere. */
    @Volatile
    override var generation = 0
        private set

    @Volatile
    private var retryAfterNanos = 0L

    @Volatile
    private var failedReopens = 0

    override fun run(command: String, timeoutMillis: Long): String? = run(command, timeoutMillis, generation)

    override fun run(command: String, timeoutMillis: Long, wanted: Int): String? {
        synchronized(commandLock) {
            // Switched off since this command was decided on, or while it waited
            // its turn: it must not bring the shell back.
            val current = synchronized(stateLock) { if (generation == wanted) session?.takeIf { it.isOpen } else null }
                ?: reopen(wanted)
                ?: return null
            val output = current.exec(command, timeoutMillis)
            if (output == null) {
                // An unanswered command leaves the stream in an unknown state, so
                // the shell goes. Not reopened at once: a command that keeps
                // timing out would otherwise start a new su, and have the root
                // manager announce it, on every poll.
                current.close()
                synchronized(stateLock) { if (session === current) session = null }
                retryAfter(TIMEOUT_RETRY_DELAY_MILLIS)
            }
            return output
        }
    }

    override suspend fun requestAccess(): RootAccess = withContext(Dispatchers.IO) {
        close()
        val wanted = synchronized(stateLock) { generation }
        val opened = try {
            ShellSession.open(binary)
        } catch (_: IOException) {
            return@withContext RootAccess.NO_SU
        }
        if (awaitRoot(opened, GRANT_TIMEOUT_MILLIS, wanted)) {
            retryAfterNanos = 0L
            failedReopens = 0
            _lost.value = false
            RootAccess.GRANTED
        } else {
            RootAccess.DENIED
        }
    }

    /**
     * Not under [commandLock]: a command in flight, or a grant the root manager is
     * still asking about, holds on for seconds, and ending the process is what ends it.
     */
    override fun close() {
        synchronized(stateLock) {
            generation++
            opening?.close()
            opening = null
            session?.close()
            session = null
        }
    }

    /**
     * Reopens after an app restart or a dead shell. A refusal is retried only a
     * few times, minutes apart, and then not at all: a revoked grant would
     * otherwise make the root manager prompt, or announce a denial, for ever.
     */
    private fun reopen(wanted: Int): ShellSession? {
        // Checked before anything starts su: an unwanted su is itself the harm.
        if (_lost.value || synchronized(stateLock) { generation } != wanted) return null
        val now = nanoTime()
        if (retryAfterNanos != 0L && now - retryAfterNanos < 0) return null
        val opened = try {
            ShellSession.open(binary)
        } catch (_: IOException) {
            null
        }
        if (opened != null && awaitRoot(opened, REOPEN_TIMEOUT_MILLIS, wanted)) {
            retryAfterNanos = 0L
            failedReopens = 0
            return opened
        }
        // Closed meanwhile: that is the user switching root off, not root being gone.
        if (synchronized(stateLock) { generation } != wanted) return null
        failedReopens++
        if (failedReopens >= MAX_FAILED_REOPENS) _lost.value = true
        retryAfter(REFUSAL_RETRY_DELAY_MILLIS)
        return null
    }

    private fun retryAfter(delayMillis: Long) {
        retryAfterNanos = (nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis)).let { if (it == 0L) 1L else it }
    }

    /** Keeps [opened] as the session when it turns out to be root and is still wanted; closes it otherwise. */
    private fun awaitRoot(opened: ShellSession, timeoutMillis: Long, wanted: Int): Boolean {
        synchronized(stateLock) {
            if (generation != wanted) {
                opened.close()
                return false
            }
            opening = opened
        }
        val granted = opened.isRoot(timeoutMillis)
        synchronized(stateLock) {
            // Closed while the root manager was asking: off wins.
            val stillWanted = opening === opened && generation == wanted
            if (opening === opened) opening = null
            if (granted && stillWanted) {
                session = opened
                return true
            }
        }
        opened.close()
        return false
    }

    private companion object {
        /** Longer than the longest prompt timeout a root manager offers (60 s). */
        const val GRANT_TIMEOUT_MILLIS = 65_000L
        const val REOPEN_TIMEOUT_MILLIS = 10_000L
        const val REFUSAL_RETRY_DELAY_MILLIS = 5 * 60_000L
        const val TIMEOUT_RETRY_DELAY_MILLIS = 60_000L
        const val MAX_FAILED_REOPENS = 3
    }
}
