package org.jarsi.devicewatch.data

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs in a process of its own that Shizuku starts as the ADB shell user, outside
 * the app: no Application, no injection, only what this file brings. It hands the
 * app the two ends of a shell, and [ShellSession] holds the conversation from there,
 * so a dump of any size streams through pipes instead of a Binder transaction.
 */
@Keep // Shizuku creates it by name.
class ShellUserService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun openShell(): Array<ParcelFileDescriptor> {
        // The pipes first: should one fail, there is no shell yet to leave behind.
        val toShell = ParcelFileDescriptor.createPipe()
        val fromShell = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: IOException) {
            toShell.forEach(::closeQuietly)
            throw e
        }
        val process = try {
            ProcessBuilder("sh").redirectErrorStream(true).start()
        } catch (e: IOException) {
            (toShell + fromShell).forEach(::closeQuietly)
            throw e
        }
        // The app closing its end is the end of the shell.
        pump(ParcelFileDescriptor.AutoCloseInputStream(toShell[0]), process.outputStream, process::destroy)
        pump(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(fromShell[1]))
        return arrayOf(toShell[1], fromShell[0])
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor) {
        try {
            descriptor.close()
        } catch (_: IOException) {
        }
    }

    private fun pump(from: InputStream, to: OutputStream, onEnd: () -> Unit = {}) {
        thread(isDaemon = true, name = "shell-pump") {
            try {
                val buffer = ByteArray(8192)
                while (true) {
                    val count = from.read(buffer)
                    if (count < 0) break
                    to.write(buffer, 0, count)
                    to.flush()
                }
            } catch (_: IOException) {
                // One side went away; closing below tells the other.
            } finally {
                try {
                    from.close()
                } catch (_: IOException) {
                }
                try {
                    to.close()
                } catch (_: IOException) {
                }
                onEnd()
            }
        }
    }
}
