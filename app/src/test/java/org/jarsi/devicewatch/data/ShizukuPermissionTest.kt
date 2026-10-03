package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShizukuPermissionTest {

    /** Shizuku as the request sees it: what it knows, what it was asked, who is listening. */
    private class FakeShizuku : ShizukuPermissionApi {
        override var running = true
        override var granted = false
        override var refusedForGood = false
        var requestFails = false
        val requested = mutableListOf<Int>()
        val resultListeners = mutableListOf<(Int, Boolean) -> Unit>()
        val deathListeners = mutableListOf<() -> Unit>()

        override fun request(requestCode: Int) {
            if (requestFails) throw IllegalStateException("binder gone")
            requested += requestCode
        }

        override fun onResult(listener: (requestCode: Int, granted: Boolean) -> Unit): AutoCloseable {
            resultListeners += listener
            return AutoCloseable { resultListeners -= listener }
        }

        override fun onBinderDead(listener: () -> Unit): AutoCloseable {
            deathListeners += listener
            return AutoCloseable { deathListeners -= listener }
        }

        fun answer(requestCode: Int, granted: Boolean) = resultListeners.toList().forEach { it(requestCode, granted) }

        fun die() = deathListeners.toList().forEach { it() }
    }

    @Test
    fun `Shizuku dying while its dialog is up ends the request, and nothing is left listening`() = runTest {
        val shizuku = FakeShizuku()
        val answer = async { awaitShizukuPermission(shizuku, requestCode = 7) }
        runCurrent()
        assertThat(shizuku.requested).containsExactly(7)
        assertThat(answer.isCompleted).isFalse()

        shizuku.die()

        assertThat(answer.await()).isEqualTo(ShizukuAccess.NOT_RUNNING)
        assertThat(shizuku.resultListeners).isEmpty()
        assertThat(shizuku.deathListeners).isEmpty()
    }

    @Test
    fun `the answer ends the request once, and a death after it changes nothing`() = runTest {
        val shizuku = FakeShizuku()
        val answer = async { awaitShizukuPermission(shizuku, requestCode = 7) }
        runCurrent()

        shizuku.answer(requestCode = 7, granted = true)
        shizuku.die()

        assertThat(answer.await()).isEqualTo(ShizukuAccess.GRANTED)
        assertThat(shizuku.resultListeners).isEmpty()
        assertThat(shizuku.deathListeners).isEmpty()
    }

    @Test
    fun `an answer to some other request is not this one's`() = runTest {
        val shizuku = FakeShizuku()
        val answer = async { awaitShizukuPermission(shizuku, requestCode = 7) }
        runCurrent()

        shizuku.answer(requestCode = 8, granted = true)
        assertThat(answer.isCompleted).isFalse()
        shizuku.answer(requestCode = 7, granted = false)

        assertThat(answer.await()).isEqualTo(ShizukuAccess.DENIED)
    }

    @Test
    fun `what Shizuku already knows is answered without a dialog`() = runTest {
        val down = FakeShizuku().apply { running = false }
        assertThat(awaitShizukuPermission(down, requestCode = 7)).isEqualTo(ShizukuAccess.NOT_RUNNING)

        val granted = FakeShizuku().apply { granted = true }
        assertThat(awaitShizukuPermission(granted, requestCode = 7)).isEqualTo(ShizukuAccess.GRANTED)

        val refused = FakeShizuku().apply { refusedForGood = true }
        assertThat(awaitShizukuPermission(refused, requestCode = 7)).isEqualTo(ShizukuAccess.DENIED)

        for (shizuku in listOf(down, granted, refused)) {
            assertThat(shizuku.requested).isEmpty()
            assertThat(shizuku.resultListeners).isEmpty()
            assertThat(shizuku.deathListeners).isEmpty()
        }
    }

    @Test
    fun `a request Shizuku cannot take is Shizuku being down`() = runTest {
        val shizuku = FakeShizuku().apply { requestFails = true }

        assertThat(awaitShizukuPermission(shizuku, requestCode = 7)).isEqualTo(ShizukuAccess.NOT_RUNNING)
        assertThat(shizuku.resultListeners).isEmpty()
        assertThat(shizuku.deathListeners).isEmpty()
    }
}
