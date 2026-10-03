package org.jarsi.devicewatch.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The few things a request for Shizuku's permission asks of Shizuku, behind an
 * interface so the conversation itself can be tested without Shizuku.
 */
internal interface ShizukuPermissionApi {
    /** False while Shizuku is down, or too old to be asked. */
    val running: Boolean

    val granted: Boolean

    /** Refused with "don't ask again": Shizuku would show no dialog. */
    val refusedForGood: Boolean

    /** Has Shizuku show its dialog; the answer comes to [onResult] listeners. */
    fun request(requestCode: Int)

    /** Hears every answer Shizuku gives; closing the handle stops listening. */
    fun onResult(listener: (requestCode: Int, granted: Boolean) -> Unit): AutoCloseable

    /** Hears Shizuku going away; closing the handle stops listening. */
    fun onBinderDead(listener: () -> Unit): AutoCloseable
}

/**
 * Asks Shizuku for its permission and waits for the user's answer. Shizuku
 * stopping meanwhile takes its dialog with it, so that too ends the request,
 * as NOT_RUNNING; a request left waiting would otherwise never return, and the
 * switch that started it would stay stuck.
 */
internal suspend fun awaitShizukuPermission(api: ShizukuPermissionApi, requestCode: Int): ShizukuAccess {
    try {
        if (!api.running) return ShizukuAccess.NOT_RUNNING
        if (api.granted) return ShizukuAccess.GRANTED
        if (api.refusedForGood) return ShizukuAccess.DENIED
    } catch (_: RuntimeException) {
        // The service went away between the ping and the call.
        return ShizukuAccess.NOT_RUNNING
    }
    return suspendCancellableCoroutine { continuation ->
        val listening = ArrayList<AutoCloseable>(2)
        fun finish(result: ShizukuAccess) {
            listening.forEach(AutoCloseable::close)
            if (continuation.isActive) continuation.resume(result)
        }
        listening += api.onResult { answered, granted ->
            if (answered == requestCode) finish(if (granted) ShizukuAccess.GRANTED else ShizukuAccess.DENIED)
        }
        listening += api.onBinderDead { finish(ShizukuAccess.NOT_RUNNING) }
        continuation.invokeOnCancellation { listening.forEach(AutoCloseable::close) }
        try {
            api.request(requestCode)
        } catch (_: RuntimeException) {
            finish(ShizukuAccess.NOT_RUNNING)
        }
    }
}
