package org.jarsi.devicewatch.data

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jarsi.devicewatch.BuildConfig
import rikka.shizuku.Shizuku
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a request for Shizuku's permission ended. */
enum class ShizukuAccess { GRANTED, DENIED, NOT_RUNNING }

/**
 * A shell run as the ADB shell user by Shizuku, on a phone that is not rooted;
 * used only while [PrivilegedAccess.SHIZUKU] is selected.
 */
interface ShizukuShell : PrivilegedShell {
    /**
     * Whether the Shizuku service is up. It stops at every reboot until the user
     * starts it again, while the permission it granted stays.
     */
    val alive: StateFlow<Boolean>

    /** Asks Shizuku for its permission; the caller selects Shizuku only after GRANTED. */
    suspend fun requestAccess(): ShizukuAccess
}

/**
 * Talks to [ShellUserService], which Shizuku runs as the shell user, and keeps one
 * [ShellSession] over the pipes it hands back.
 */
@Singleton
class ShizukuUserShell internal constructor(
    private val permissions: ShizukuPermissionApi,
) : ShizukuShell {

    @Inject
    constructor() : this(StaticShizukuPermissionApi)

    private val _alive = MutableStateFlow(false)
    override val alive: StateFlow<Boolean> = _alive

    /** Serializes commands; never held by [close], which must not wait for one. */
    private val lock = Any()

    @Volatile
    private var session: ShellSession? = null

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var bound = CountDownLatch(1)

    /** Moves on every [close], so a command that was already on its way can tell it is no longer wanted. */
    @Volatile
    private var generation = 0

    /** Set after a shell that did not open or did not answer, so the next commands do not each wait it out again. */
    @Volatile
    private var retryAfterNanos = 0L

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShellService.Stub.asInterface(binder)
            bound.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            forgetService()
        }
    }

    init {
        // Sticky: says so at once when the service was already up before this object.
        Shizuku.addBinderReceivedListenerSticky {
            _alive.value = true
            // A Shizuku just started is a new chance at once.
            retryAfterNanos = 0L
        }
        Shizuku.addBinderDeadListener {
            _alive.value = false
            forgetService()
        }
    }

    override fun run(command: String, timeoutMillis: Long): String? {
        val wanted = generation
        synchronized(lock) {
            // Switched off while this command waited its turn: it must not bring
            // the shell back.
            if (generation != wanted) return null
            val current = session?.takeIf { it.isOpen } ?: open(wanted) ?: return null
            val output = current.exec(command, timeoutMillis)
            if (output == null) {
                // An unanswered command leaves the stream in an unknown state, so
                // the shell goes; a command that keeps timing out must not start
                // a new one on every poll.
                current.close()
                if (session === current) session = null
                retryAfter()
            }
            return output
        }
    }

    override suspend fun requestAccess(): ShizukuAccess = awaitShizukuPermission(permissions, PERMISSION_REQUEST_CODE)

    /** Not under [lock]: ending the shell is what releases a command in flight. */
    override fun close() {
        generation++
        retryAfterNanos = 0L
        unbind()
        forgetService()
    }

    /**
     * Ends the service process as well. Asked for even when no connection has
     * arrived yet: a bind still on its way would otherwise leave the process
     * running after the switch went off.
     */
    private fun unbind() {
        try {
            if (Shizuku.pingBinder()) Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: RuntimeException) {
            // Shizuku is gone, and its service with it.
        }
    }

    private fun forgetService() {
        service = null
        session?.close()
        session = null
        bound = CountDownLatch(1)
    }

    private fun open(wanted: Int): ShellSession? {
        val now = System.nanoTime()
        if (retryAfterNanos != 0L && now - retryAfterNanos < 0) return null
        val remote = service ?: bind()
        if (remote == null) {
            // Shizuku being down answers at once and needs no pause; a service that
            // never connected cost the whole wait, and would again on every command.
            if (_alive.value) retryAfter()
            return null
        }
        val pipes = try {
            remote.openShell()
        } catch (_: RemoteException) {
            null
        } catch (_: RuntimeException) {
            null
        }
        if (pipes == null || pipes.size != 2) {
            pipes?.forEach(::closeQuietly)
            unbind()
            forgetService()
            retryAfter()
            return null
        }
        val input = ParcelFileDescriptor.AutoCloseInputStream(pipes[1])
        // Closing the input as well: a child of the shell can outlive it and keep
        // the pipe open, and the reader thread would wait on it for ever.
        val opened = ShellSession(input, ParcelFileDescriptor.AutoCloseOutputStream(pipes[0])) {
            try {
                input.close()
            } catch (_: IOException) {
                // Already gone.
            }
        }
        if (generation != wanted) {
            // Switched off while the shell was being opened: off wins.
            opened.close()
            unbind()
            forgetService()
            return null
        }
        session = opened
        return opened
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (_: IOException) {
            // Already gone.
        }
    }

    private fun retryAfter() {
        retryAfterNanos = (System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RETRY_DELAY_MILLIS))
            .let { if (it == 0L) 1L else it }
    }

    /**
     * Has Shizuku start the service and waits for it. The connection arrives on
     * the main thread, which is why [run] must never be called there.
     */
    private fun bind(): IShellService? {
        val waiting = bound
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return null
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (_: RuntimeException) {
            return null
        }
        try {
            waiting.await(BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return service
    }

    private companion object {
        const val PERMISSION_REQUEST_CODE = 7301
        const val BIND_TIMEOUT_MILLIS = 5_000L
        const val RETRY_DELAY_MILLIS = 60_000L
    }
}

/** Shizuku itself, as [awaitShizukuPermission] talks to it. */
internal object StaticShizukuPermissionApi : ShizukuPermissionApi {
    override val running: Boolean get() = Shizuku.pingBinder() && !Shizuku.isPreV11()

    override val granted: Boolean get() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override val refusedForGood: Boolean get() = Shizuku.shouldShowRequestPermissionRationale()

    override fun request(requestCode: Int) = Shizuku.requestPermission(requestCode)

    override fun onResult(listener: (requestCode: Int, granted: Boolean) -> Unit): AutoCloseable {
        val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            listener(requestCode, grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        return AutoCloseable { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
    }

    override fun onBinderDead(listener: () -> Unit): AutoCloseable {
        val shizukuListener = Shizuku.OnBinderDeadListener { listener() }
        Shizuku.addBinderDeadListener(shizukuListener)
        return AutoCloseable { Shizuku.removeBinderDeadListener(shizukuListener) }
    }
}
