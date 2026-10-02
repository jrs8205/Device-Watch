package org.jarsi.devicewatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a request for root ended. */
enum class RootAccess { GRANTED, DENIED, NO_SU }

/** A root shell on a rooted phone; used only while [PrivilegedAccess.ROOT] is selected. */
interface RootShell : PrivilegedShell {
    /**
     * Opens a root shell, which is what makes the root manager ask the user. The
     * caller selects root access only after GRANTED.
     */
    suspend fun requestAccess(): RootAccess
}

/** One long-lived `su` process fed commands over stdin ([ShellSession]). */
@Singleton
class SuRootShell internal constructor(private val binary: String) : RootShell {

    @Inject
    constructor() : this("su")

    /** Serializes commands; never held by [close], which must not wait for one. */
    private val lock = Any()

    @Volatile
    private var session: ShellSession? = null

    /** A shell still waiting for the root manager's answer. */
    @Volatile
    private var opening: ShellSession? = null

    @Volatile
    private var retryAfterNanos = 0L

    override fun run(command: String, timeoutMillis: Long): String? {
        synchronized(lock) {
            val current = session?.takeIf { it.isOpen } ?: reopen() ?: return null
            val output = current.exec(command, timeoutMillis)
            // An unanswered command leaves the stream in an unknown state.
            if (output == null) {
                current.close()
                if (session === current) session = null
            }
            return output
        }
    }

    override suspend fun requestAccess(): RootAccess = withContext(Dispatchers.IO) {
        close()
        val opened = try {
            ShellSession.open(binary)
        } catch (_: IOException) {
            return@withContext RootAccess.NO_SU
        }
        if (awaitRoot(opened, GRANT_TIMEOUT_MILLIS)) {
            retryAfterNanos = 0L
            RootAccess.GRANTED
        } else {
            RootAccess.DENIED
        }
    }

    /**
     * Not under [lock]: a command in flight, or a grant the root manager is still
     * asking about, holds on for seconds, and ending the process is what ends it.
     */
    override fun close() {
        opening?.close()
        opening = null
        session?.close()
        session = null
    }

    /**
     * Reopens after an app restart or a dead shell. A refusal is not retried at
     * once: a revoked grant would otherwise make the root manager announce a
     * denial on every poll.
     */
    private fun reopen(): ShellSession? {
        val now = System.nanoTime()
        if (retryAfterNanos != 0L && now - retryAfterNanos < 0) return null
        val opened = try {
            ShellSession.open(binary)
        } catch (_: IOException) {
            null
        }
        if (opened != null && awaitRoot(opened, REOPEN_TIMEOUT_MILLIS)) {
            retryAfterNanos = 0L
            return opened
        }
        retryAfterNanos = (now + TimeUnit.MILLISECONDS.toNanos(RETRY_DELAY_MILLIS)).let { if (it == 0L) 1L else it }
        return null
    }

    /** Keeps [opened] as the session when it turns out to be root; closes it otherwise. */
    private fun awaitRoot(opened: ShellSession, timeoutMillis: Long): Boolean {
        opening = opened
        val granted = opened.isRoot(timeoutMillis)
        // Closed while the root manager was asking: off wins.
        val stillWanted = opening === opened
        if (stillWanted) opening = null
        if (granted && stillWanted) {
            session = opened
            return true
        }
        opened.close()
        return false
    }

    private companion object {
        /** Longer than the longest prompt timeout a root manager offers (60 s). */
        const val GRANT_TIMEOUT_MILLIS = 65_000L
        const val REOPEN_TIMEOUT_MILLIS = 10_000L
        const val RETRY_DELAY_MILLIS = 5 * 60_000L
    }
}
