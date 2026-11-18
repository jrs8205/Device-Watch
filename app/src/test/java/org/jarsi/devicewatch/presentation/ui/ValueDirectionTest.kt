package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.data.TrafficMeter
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.presentation.freqText
import org.jarsi.devicewatch.system.dream.DreamChargeState
import org.jarsi.devicewatch.system.dream.batteryTempText
import org.jarsi.devicewatch.system.dream.batteryVoltsText
import org.jarsi.devicewatch.system.dream.batteryWattsText
import org.jarsi.devicewatch.widget.compactDataAmountText
import org.jarsi.devicewatch.widget.dataAmountText
import org.jarsi.devicewatch.widget.formatDouble
import org.jarsi.devicewatch.widget.gbText
import org.jarsi.devicewatch.widget.speedText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import org.jarsi.devicewatch.widget.percentText as wholePercentText

/**
 * A number and its unit in a language written from the right. Left alone, "25.0 °C" is laid
 * out from the right like the text around it: the unit lands before the number and the
 * degree sign on the wrong side of its letter ("C° 25.0"), and two such values on one line
 * swap their parts. A value with a Latin unit is one left-to-right piece, wherever it is put.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ValueDirectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val arabic: Locale = Locale.forLanguageTag("ar-EG-u-nu-latn")

    private companion object {
        const val ARABIC_PHONE = "ar-rEG-w360dp-h780dp-port-mdpi"
    }
    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun restoreDefault() {
        Locale.setDefault(defaultBefore)
    }

    private fun isolated(text: String): String = Char(0x2066) + text + Char(0x2069)

    @Test
    fun `a value with a Latin unit is one left-to-right piece in Arabic`() {
        Locale.setDefault(arabic)
        val values = mapOf(
            formatDouble(25.5, 1, " °C") to "25.5 °C",
            gbText(5.0) to "5.0 GB",
            speedText(120) to "120 Mb/s",
            dataAmountText(1.5) to "1.50 GB",
            dataAmountText(0.03) to "31 MB",
            dataAmountText(0.0) to "0 MB",
            compactDataAmountText(5.0) to "5.0 GB",
            compactDataAmountText(20.0) to "20 GB",
            mahText(4040.0, arabic) to "4040 mAh",
            percentText(12.3, arabic) to "12.3 %",
            celsiusText(25.4) to "25 °C",
            hoursText(13.2, arabic) to "13 h",
            dbmText(-67) to "-67 dBm",
            spacedPercentText(100) to "100 %",
            mbpsText(866) to "866 Mbps",
            TrafficMeter.rateText(10_625L, arabic) to "85 kb/s",
            TrafficMeter.rateText(3_000_000L, arabic) to "24 Mb/s",
            freqText(2_294_000L, arabic) to "2.29 GHz",
            freqText(820_000L, arabic) to "820 MHz",
            batteryVoltsText(3.82, arabic) to "3.82 V",
            batteryTempText(26.3, arabic) to "26.3 °C",
            batteryWattsText(11.0, DreamChargeState.CHARGING, arabic) to "11.0 W",
        )

        for ((actual, plain) in values) {
            assertWithMessage(plain).that(actual).isEqualTo(isolated(plain))
        }
    }

    // "−350 MB" with its sign outside the piece came out as "350 MB−".
    @Test
    fun `a change in used storage keeps its sign in front in Arabic`() {
        Locale.setDefault(arabic)
        val megabytes = 350L * 1024 * 1024

        for ((text, plain) in mapOf(
            storageChangeText(-megabytes) to Char(0x2212) + "350 MB",
            storageChangeText(megabytes) to "+350 MB",
        )) {
            assertThat(text.first().code).isEqualTo(0x2066)
            assertThat(text.last().code).isEqualTo(0x2069)
            assertThat(text.filterNot { it.code in 0x2066..0x2069 }).isEqualTo(plain)
        }
    }

    @Test
    fun `a bare number, a per cent without a space and a dash need no wrapping`() {
        Locale.setDefault(arabic)

        assertThat(formatDouble(3.0, 1)).isEqualTo("3.0")
        assertThat(wholePercentText(45)).isEqualTo("45%")
        assertThat(gbText(-1.0)).isEqualTo(UNAVAILABLE_TEXT)
        assertThat(dataAmountText(-1.0)).isEqualTo(UNAVAILABLE_TEXT)
        assertThat(speedText(-1)).isEqualTo(UNAVAILABLE_TEXT)
    }

    @Test
    fun `in English the values are as they were`() {
        Locale.setDefault(Locale.US)

        assertThat(gbText(5.0)).isEqualTo("5.0 GB")
        assertThat(celsiusText(25.4)).isEqualTo("25 °C")
        assertThat(TrafficMeter.rateText(10_625L, Locale.US)).isEqualTo("85 kb/s")
        assertThat(batteryTempText(26.3, Locale.US)).isEqualTo("26.3 °C")
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun showValue(text: String, direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MetricValue(text, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // On the screen a value's own first letter decides: a Latin unit reads from the left, an
    // Arabic one from the right. Either way it sits at the side where reading starts.
    //
    // What a phone does with a text left alone cannot be seen here: on an Arabic phone it is
    // laid out from the right whatever it holds, while this test's text engine goes by the
    // first letter by itself. So the tests hold the code to what it asks for (a direction by
    // content) and to where the value sits, and the phone's result was checked on a device.
    @Test
    @Config(qualifiers = ARABIC_PHONE)
    fun `right to left a value with a Latin unit reads from the left and sits at the right`() {
        showValue("25.0 °C", LayoutDirection.Rtl)

        val layout = layoutOf("25.0 °C")
        assertThat(layout.layoutInput.style.textDirection).isEqualTo(TextDirection.Content)
        assertThat(layout.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Ltr)
        assertThat(layout.getLineRight(0)).isWithin(1f).of(layout.size.width.toFloat())
        assertThat(layout.getLineLeft(0)).isGreaterThan(0f)
    }

    @Test
    @Config(qualifiers = ARABIC_PHONE)
    fun `right to left a value with an Arabic unit reads from the right`() {
        val minutes = "55 " + Char(0x062F)
        showValue(minutes, LayoutDirection.Rtl)

        val layout = layoutOf(minutes)
        assertThat(layout.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Rtl)
        assertThat(layout.getLineRight(0)).isWithin(1f).of(layout.size.width.toFloat())
    }

    @Test
    @Config(qualifiers = ARABIC_PHONE)
    fun `right to left a fact about the device reads by its own first letter too`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DeviceFact(label = "Android", value = "16 (API 36)")
            }
        }

        assertThat(layoutOf("16 (API 36)").layoutInput.style.textDirection).isEqualTo(TextDirection.Content)
    }

    @Test
    @Config(qualifiers = ARABIC_PHONE)
    fun `right to left a kernel row's plain value reads by its own first letter too`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KernelRow(label = "Governor", value = "schedutil (walt)")
            }
        }

        assertThat(layoutOf("schedutil (walt)").layoutInput.style.textDirection).isEqualTo(TextDirection.Content)
    }

    @Test
    fun `left to right a value sits at the left as before`() {
        showValue("25.0 °C", LayoutDirection.Ltr)

        val layout = layoutOf("25.0 °C")
        assertThat(layout.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Ltr)
        assertThat(layout.getLineLeft(0)).isEqualTo(0f)
    }
}
