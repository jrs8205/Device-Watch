package org.jarsi.devicewatch.system.dream

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.jarsi.devicewatch.LatinDigits
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Where the notification row's icons are decoded ([LocalNotificationIconDecoding]).
 *
 * In the app that is off the main thread, and stays so. A test that draws the screen provides
 * its own thread: left on the other dispatcher, the test library ran whole frames of the
 * screensaver there, recomposing and measuring it while the test's own thread measured it
 * too, and a run in some dozens stopped with "performMeasureAndLayout called during measure
 * layout" (first seen on 2026-11-20, then again; a loop of the fit test's own drawing brought
 * it up within a thousand rounds).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w320dp-h640dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationIconDecodingTest {

    @get:Rule
    val compose = createComposeRule()

    private val defaultsBefore = Triple(
        Locale.getDefault(),
        Locale.getDefault(Locale.Category.DISPLAY),
        Locale.getDefault(Locale.Category.FORMAT),
    )

    @After
    fun restoreDefaults() {
        Locale.setDefault(defaultsBefore.first)
        Locale.setDefault(Locale.Category.DISPLAY, defaultsBefore.second)
        Locale.setDefault(Locale.Category.FORMAT, defaultsBefore.third)
    }

    @Test
    fun `in the app the icons are decoded off the main thread`() {
        var decoding: CoroutineContext? = null
        compose.setContent { decoding = LocalNotificationIconDecoding.current }
        compose.waitForIdle()

        assertThat(decoding).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `decoded in place, every state the screen is drawn from is written on the test's own thread`() {
        LatinDigits.applyToDefault()
        val context = LatinDigits.context(ApplicationProvider.getApplicationContext<Application>())
        val state = worstCaseDreamUiState(context, DreamChargeState.CHARGING)
        val testThread = Thread.currentThread()
        // The threads that hand a written state on to the composition: whichever does is the
        // one the test library then runs the frame on.
        val appliedOn = Collections.synchronizedSet(mutableSetOf<Thread>())
        val observer = Snapshot.registerApplyObserver { _, _ -> appliedOn += Thread.currentThread() }
        try {
            // A new number has the screensaver, and with it every icon, made anew.
            var drawing by mutableStateOf(0)
            compose.setContent {
                CompositionLocalProvider(
                    LocalContext provides LatinDigits.context(LocalContext.current),
                    LocalNotificationIconDecoding provides EmptyCoroutineContext,
                ) {
                    key(drawing) {
                        DreamScreen(state = state, options = DreamOptions(), landscape = false, onTap = null, moving = false)
                    }
                }
            }
            repeat(ROUNDS) { round ->
                drawing = round
                compose.waitForIdle()
                // The icons are in: a count is drawn only beside its icon.
                compose.waitUntil(timeoutMillis = 5_000) {
                    compose.onAllNodesWithText(WORST_CASE_FIRST_COUNT.toString()).fetchSemanticsNodes().isNotEmpty()
                }
            }
        } finally {
            observer.dispose()
        }
        assertThat(appliedOn.map { it.name }).containsExactly(testThread.name)
    }

    private companion object {
        /** Each round decodes every icon of the row again. */
        const val ROUNDS = 10
    }
}
