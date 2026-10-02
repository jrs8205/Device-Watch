package org.jarsi.devicewatch.data

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Which shell reads what Android denies an ordinary app: none, the ADB shell user
 * through Shizuku, or root. Opt-in, and set only after that route granted access.
 */
enum class PrivilegedAccess { OFF, SHIZUKU, ROOT }

/** Commands run above an ordinary app's rights. */
interface PrivilegedShell {
    /**
     * Runs [command] and returns its standard output. Null when privileged access
     * is off or the shell could not answer; an empty string when the command ran
     * and printed nothing. Blocks, so never call it on the main thread.
     */
    fun run(command: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String?

    /** Ends the shell; the next [run] opens a new one. Never blocks. */
    fun close()

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 3_000L
    }
}

/**
 * The shell [AppSettingsRepository.privilegedAccess] names. While that is off
 * nothing here starts a process or calls out, so a phone that never opted in sees
 * neither an `su` attempt nor a Shizuku request.
 */
@Singleton
class SelectedPrivilegedShell @Inject constructor(
    private val settings: AppSettingsRepository,
    private val root: RootShell,
    private val shizuku: ShizukuShell,
) : PrivilegedShell {

    override fun run(command: String, timeoutMillis: Long): String? =
        when (settings.privilegedAccess()) {
            PrivilegedAccess.OFF -> null
            PrivilegedAccess.SHIZUKU -> shizuku.run(command, timeoutMillis)
            PrivilegedAccess.ROOT -> root.run(command, timeoutMillis)
        }

    override fun close() {
        root.close()
        shizuku.close()
    }
}

/**
 * One poll's use of the privileged shell. A shell that fails to answer once is
 * not asked again for the rest of the poll: every further command would wait
 * out its own timeout, and a poll that reads twenty things would hold up
 * everything queued behind it for a minute.
 */
internal class ShellPass(private val shell: PrivilegedShell, usable: Boolean) {

    var usable = usable
        private set

    fun run(command: String, timeoutMillis: Long = PrivilegedShell.DEFAULT_TIMEOUT_MILLIS): String? {
        if (!usable) return null
        val output = shell.run(command, timeoutMillis)
        if (output == null) usable = false
        return output
    }
}

/** The pure half of the shell conversation: how a command is framed and its end found. */
internal object ShellProtocol {

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
 * One long-lived shell and the framed conversation with it, over whatever carries
 * its input and output: a `su` process, or the two pipes a Shizuku shell hands over.
 * A process per command would make a root manager log, and by default announce,
 * every single read.
 */
internal class ShellSession(
    input: InputStream,
    output: OutputStream,
    private val onClose: () -> Unit = {},
) {

    private val lines = LinkedBlockingQueue<String>()
    private val writer = output.bufferedWriter()

    @Volatile
    var isOpen = true
        private set

    init {
        thread(isDaemon = true, name = "shell-reader") {
            try {
                input.bufferedReader().forEachLine(lines::put)
            } catch (_: IOException) {
                // The shell went away; the end marker below says so.
            } finally {
                isOpen = false
                lines.put(END_OF_STREAM)
            }
        }
    }

    fun isRoot(timeoutMillis: Long): Boolean = exec("id -u", timeoutMillis)?.trim() == "0"

    fun exec(command: String, timeoutMillis: Long): String? {
        val marker = ShellProtocol.newMarker()
        try {
            writer.write(ShellProtocol.frame(command, marker))
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
            val last = ShellProtocol.beforeMarker(line, marker)
            if (last != null) {
                if (last.isNotEmpty()) output += last
                return output.joinToString("\n")
            }
            output += line
        }
    }

    /**
     * Safe from any thread and never waits: ending the shell is what releases a
     * command still in flight on another thread.
     */
    fun close() {
        isOpen = false
        onClose()
        // Said here as well: a child of the shell can outlive it and keep the
        // output open, and the reader would then never see the end.
        lines.put(END_OF_STREAM)
        // After the shell is gone, so a writer blocked on a full pipe cannot hold this up.
        try {
            writer.close()
        } catch (_: IOException) {
            // Already gone.
        }
    }

    companion object {
        @Suppress("StringOperationCanBeSimplified") // a distinct instance is the point
        private val END_OF_STREAM = String("end".toCharArray())

        @Throws(IOException::class)
        fun open(shell: String): ShellSession {
            val process = ProcessBuilder(shell).redirectErrorStream(true).start()
            return ShellSession(process.inputStream, process.outputStream, process::destroy)
        }
    }
}
