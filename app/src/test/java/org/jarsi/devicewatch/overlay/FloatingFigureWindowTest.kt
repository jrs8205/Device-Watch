package org.jarsi.devicewatch.overlay

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.quick.QuickMetricReading
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/** The card as a real window under Robolectric: added, laid out and updated through the window manager. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloatingFigureWindowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val window = FloatingFigureWindow(
        context,
        onHide = {},
        onOpenApp = {},
        onPositionChanged = { _, _ -> },
        onFailed = { error("the window failed") },
    )

    @Test
    fun `a value that grows longer is shown whole after an update`() {
        // External review round 2: the value and label took the figure's match-parent
        // width, so a value one character longer with the same height was never
        // re-measured and ended in an ellipsis instead of widening the card.
        window.show(listOf(storage("99 / 128 GB")), position = null, landscape = false, black = false)
        layOut()
        val value = textView("99 / 128 GB")
        val narrow = value.width

        window.update(listOf(storage("100 / 128 GB")))
        layOut()

        assertThat(value.text.toString()).isEqualTo("100 / 128 GB")
        assertThat(value.layout.getEllipsisCount(0)).isEqualTo(0)
        assertThat(value.width).isGreaterThan(narrow)
    }

    @Test
    fun `a label that grows longer is shown whole after an update`() {
        window.show(listOf(battery("78 %", detail = null)), position = null, landscape = false, black = false)
        layOut()
        val label = textView("Battery")
        val narrow = label.width

        window.update(listOf(battery("78 %", detail = "Charging")))
        layOut()

        assertThat(label.text.toString()).isEqualTo("Battery · Charging")
        assertThat(label.layout.getEllipsisCount(0)).isEqualTo(0)
        assertThat(label.width).isGreaterThan(narrow)
    }

    /** The windows the card has put up through the window manager. */
    private fun windowViews(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    private fun layOut() = shadowOf(Looper.getMainLooper()).idle()

    private fun textView(text: String): TextView {
        val card = windowViews().single()
        return textViews(card).single { it.text.toString() == text }
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun storage(value: String) =
        QuickMetricReading(QuickMetric.STORAGE, value, detail = null, unavailableReason = null)

    private fun battery(value: String, detail: String?) =
        QuickMetricReading(QuickMetric.BATTERY, value, detail = detail, unavailableReason = null)
}
