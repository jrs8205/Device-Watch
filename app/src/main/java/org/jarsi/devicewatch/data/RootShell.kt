package org.jarsi.devicewatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/** How a request for root ended. */
enum class RootAccess { GRANTED, DENIED, NO_SU }

/**
 * Commands run as root on a rooted phone. Root mode is opt-in
 * ([AppSettingsRepository.rootModeEnabled]); while it is off nothing here starts
 * a process, so an unrooted phone never sees an `su` attempt.
 */
interface RootShell {
    /**
     * Runs [command] as root and returns its standard output. Null when root mode
     * is off or the shell could not answer; an empty string when the command ran
     * and printed nothing. Blocks, so never call it on the main thread.
     */
    fun run(command: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String?

    /**
     * Opens a root shell regardless of the setting, which is what makes the root
     * manager ask the user. The caller switches root mode on only after GRANTED.
     */
    suspend fun requestAccess(): RootAccess

    /** Ends the root shell; the next [run] in root mode opens a new one. */
    fun close()

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 3_000L
    }
}

/** The pure half of the shell conversation: how a command is framed and its end found. */
internal object RootShellProtocol {

    private val random = SecureRandom()

    /** Unguessable per command, so no file content can pose as the end of the output. */
    fun newMarker(): String = "__dw_" + java.lang.Long.toHexString(random.nextLong()) + "__"

    /**
     * The braces scope the redirections to the whole command, whatever it contains;
     * stdin is closed off so a command that reads cannot swallow the next one.
     */
    fun frame(command: String, marker: String): String =
        "{ $command\n} 2>/dev/null </dev/null\necho $marker\n"

    /**
     * The output on [line] ahead of [marker], or null when the line does not end the
     * command. Output without a trailing newline shares its last line with the marker.
     */
    fun beforeMarker(line: String, marker: String): String? =
        if (line.endsWith(marker)) line.removeSuffix(marker) else null

    /** Single-quotes [value] for the shell. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

/**
 * One long-lived `su` process fed commands over stdin. A process per command would
 * make the root manager log, and by default announce, every single read.
 */
@Singleton
class SuRootShell @Inject constructor(
    private val settings: AppSettingsRepository,
) : RootShell {

    private val lock = Any()
    private var session: ShellSession? = null
    private var retryAfterNanos = 0L

    override fun run(command: String, timeoutMillis: Long): String? {
        // Checked before the lock: while a grant is pending the setting is still
        // off, and the monitor loop must not queue up behind the user's decision.
        if (!settings.rootModeEnabled()) return null
        synchronized(lock) {
            val current = session ?: reopen() ?: return null
            val output = current.exec(command, timeoutMillis)
            // An unanswered command leaves the stream in an unknown state.
            if (output == null) closeLocked()
            return output
        }
    }

    override suspend fun requestAccess(): RootAccess = withContext(Dispatchers.IO) {
        synchronized(lock) {
            closeLocked()
            retryAfterNanos = 0L
            val opened = try {
                ShellSession.open()
            } catch (_: IOException) {
                return@withContext RootAccess.NO_SU
            }
            if (opened.isRoot(GRANT_TIMEOUT_MILLIS)) {
                session = opened
                RootAccess.GRANTED
            } else {
                opened.close()
                RootAccess.DENIED
            }
        }
    }

    override fun close() {
        synchronized(lock) { closeLocked() }
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
            ShellSession.open()
        } catch (_: IOException) {
            null
        }
        if (opened != null && opened.isRoot(REOPEN_TIMEOUT_MILLIS)) {
            retryAfterNanos = 0L
            session = opened
            return opened
        }
        opened?.close()
        retryAfterNanos = (now + TimeUnit.MILLISECONDS.toNanos(RETRY_DELAY_MILLIS)).let { if (it == 0L) 1L else it }
        return null
    }

    private fun closeLocked() {
        session?.close()
        session = null
    }

    private companion object {
        /** Longer than the longest prompt timeout a root manager offers (60 s). */
        const val GRANT_TIMEOUT_MILLIS = 65_000L
        const val REOPEN_TIMEOUT_MILLIS = 10_000L
        const val RETRY_DELAY_MILLIS = 5 * 60_000L
    }
}

/** One shell process and the framed conversation with it; a root shell when the process is `su`. */
internal class ShellSession private constructor(private val process: Process) {

    private val lines = LinkedBlockingQueue<String>()
    private val writer = process.outputStream.bufferedWriter()

    init {
        thread(isDaemon = true, name = "root-shell-reader") {
            try {
                process.inputStream.bufferedReader().forEachLine(lines::put)
            } catch (_: IOException) {
                // The process went away; the end marker below says so.
            } finally {
                lines.put(END_OF_STREAM)
            }
        }
    }

    fun isRoot(timeoutMillis: Long): Boolean = exec("id -u", timeoutMillis)?.trim() == "0"

    fun exec(command: String, timeoutMillis: Long): String? {
        val marker = RootShellProtocol.newMarker()
        try {
            writer.write(RootShellProtocol.frame(command, marker))
            writer.flush()
        } catch (_: IOException) {
            return null
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val output = ArrayList<String>()
        while (true) {
            val line = try {
                lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } ?: return null
            // Identity, not equality: a file may well contain the same text.
            if (line === END_OF_STREAM) return null
            val last = RootShellProtocol.beforeMarker(line, marker)
            if (last != null) {
                if (last.isNotEmpty()) output += last
                return output.joinToString("\n")
            }
            output += line
        }
    }

    fun close() {
        try {
            writer.close()
        } catch (_: IOException) {
            // Already gone.
        }
        process.destroy()
    }

    companion object {
        @Suppress("StringOperationCanBeSimplified") // a distinct instance is the point
        private val END_OF_STREAM = String("end".toCharArray())

        @Throws(IOException::class)
        fun open(shell: String = "su"): ShellSession =
            ShellSession(ProcessBuilder(shell).redirectErrorStream(true).start())
    }
}
