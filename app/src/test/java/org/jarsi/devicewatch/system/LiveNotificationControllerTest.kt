package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.quick.quickStats
import org.junit.Test

class LiveNotificationControllerTest {

    private var chosen = setOf(QuickMetric.BATTERY)
    private val posts = mutableListOf<String>()
    private var postSucceeds = true
    private var quietSucceeds = true
    private var showing = true
    private var readGate: CompletableDeferred<Unit>? = null

    private val controller = LiveNotificationController(
        chosen = { chosen },
        read = { _, _ ->
            readGate?.await()
            LiveNotificationText("Akku 78%", null)
        },
        postLive = { text ->
            posts += "live:${text?.title}"
            postSucceeds
        },
        postQuiet = {
            posts += "quiet"
            quietSucceeds
        },
        removeLive = { posts += "removeLive" },
        isShowing = { showing },
    )

    @Test
    fun `clearing the figures with the silent channel blocked takes the live notification down`() = runTest {
        // External review round 2: the silent notification could not replace the live
        // one, which then stayed with its last figures for good.
        controller.refresh(quickStats())
        chosen = emptySet()
        quietSucceeds = false

        controller.onSelectionChanged()

        assertThat(posts).containsExactly("live:Akku 78%", "quiet", "removeLive").inOrder()
    }

    @Test
    fun `a notification the system took away is posted again with the same figures`() = runTest {
        // External review round 2: blocking and allowing the channel again removed the
        // notification, and the unchanged text counted as still posted.
        controller.refresh(quickStats())
        showing = false

        controller.refresh(quickStats())

        assertThat(posts).containsExactly("live:Akku 78%", "live:Akku 78%")
    }

    @Test
    fun `a swiped notification stays away although it is no longer showing`() = runTest {
        controller.refresh(quickStats())
        controller.onDismissed()
        showing = false

        controller.refresh(quickStats())

        assertThat(posts).containsExactly("live:Akku 78%")
    }

    @Test
    fun `clearing the last figure while a reading is under way ends silent`() = runTest {
        // Final review: a reading that had picked up the old choice posted its
        // live notification after the silent one, and nothing ever corrected it.
        readGate = CompletableDeferred()
        launch { controller.refresh(quickStats()) }
        advanceUntilIdle()

        chosen = emptySet()
        val followUp = async { controller.onSelectionChanged() }
        advanceUntilIdle()
        readGate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(posts.last()).isEqualTo("quiet")
        assertThat(followUp.await()).isFalse()
    }

    @Test
    fun `a post that did not go out is tried again with the same figures`() = runTest {
        // Final review: with the permission still missing, the text counted as
        // posted and stayed off the screen after the permission was granted.
        postSucceeds = false
        controller.refresh(quickStats())
        postSucceeds = true
        controller.refresh(quickStats())

        assertThat(posts).containsExactly("live:Akku 78%", "live:Akku 78%")
    }

    @Test
    fun `nothing chosen posts nothing on a reading`() = runTest {
        chosen = emptySet()
        controller.refresh(quickStats())
        assertThat(posts).isEmpty()
    }

    @Test
    fun `figures chosen ask for a fresh reading`() = runTest {
        assertThat(controller.onSelectionChanged()).isTrue()
        assertThat(posts).isEmpty()
    }
}
