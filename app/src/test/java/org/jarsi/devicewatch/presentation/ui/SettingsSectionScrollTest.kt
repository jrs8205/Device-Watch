package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.SettingsSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Settings tab's answer to a request from one of Android's own pages: the asked-for
 * section comes to the top of the page. At mdpi one dp is one pixel, so the heights below
 * are the scroll positions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSectionScrollTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var scrollState: ScrollState
    private var request by mutableStateOf<SettingsSection?>(null)
    private var heightAbove by mutableIntStateOf(500)
    private val shown = mutableListOf<SettingsSection>()

    private fun show() {
        compose.setContent {
            scrollState = rememberScrollState()
            val tops = remember { mutableStateMapOf<SettingsSection, Int>() }
            ScrollToRequestedSection(request, tops, scrollState) {
                shown += it
                if (request == it) request = null
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Box(modifier = Modifier.fillMaxWidth().height(heightAbove.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2000.dp)
                        .sectionTop(SettingsSection.Screensaver, tops)
                )
                Box(modifier = Modifier.fillMaxWidth().height(2000.dp))
            }
        }
        compose.waitForIdle()
    }

    private fun letTheSettleTimePass() {
        compose.mainClock.advanceTimeBy(SECTION_SETTLE_MILLIS + 100)
        compose.waitForIdle()
    }

    @Test
    fun `a section asked for at the start is brought to the top of the page`() {
        request = SettingsSection.Screensaver

        show()

        assertThat(scrollState.value).isEqualTo(500)
    }

    @Test
    fun `a section asked for while the page is open is brought to the top too`() {
        show()
        assertThat(scrollState.value).isEqualTo(0)

        request = SettingsSection.Screensaver
        compose.waitForIdle()

        assertThat(scrollState.value).isEqualTo(500)
    }

    @Test
    fun `the section is followed while the sections above it still change height`() {
        request = SettingsSection.Screensaver
        show()

        heightAbove = 800
        compose.waitForIdle()

        assertThat(scrollState.value).isEqualTo(800)
    }

    @Test
    fun `the request is reported shown once the settle time has passed and the page is then left alone`() {
        request = SettingsSection.Screensaver
        show()
        assertThat(shown).isEmpty()

        letTheSettleTimePass()
        assertThat(shown).containsExactly(SettingsSection.Screensaver)
        assertThat(request).isNull()

        heightAbove = 800
        compose.waitForIdle()
        assertThat(scrollState.value).isEqualTo(500)
    }

    @Test
    fun `the same section can be asked for again after it was shown`() {
        request = SettingsSection.Screensaver
        show()
        letTheSettleTimePass()
        compose.runOnIdle { scrollState.dispatchRawDelta(-500f) }
        assertThat(scrollState.value).isEqualTo(0)

        request = SettingsSection.Screensaver
        compose.waitForIdle()

        assertThat(scrollState.value).isEqualTo(500)
    }

    @Test
    fun `without a request the page stays where it is`() {
        show()
        letTheSettleTimePass()

        assertThat(scrollState.value).isEqualTo(0)
        assertThat(shown).isEmpty()
    }
}
