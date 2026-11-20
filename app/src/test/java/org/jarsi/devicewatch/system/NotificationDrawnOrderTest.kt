package org.jarsi.devicewatch.system

import android.app.Application
import android.app.Notification
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.Bidi
import org.jarsi.devicewatch.LatinDigits
import org.jarsi.devicewatch.data.QuickMetric
import org.jarsi.devicewatch.phoneLocale
import org.jarsi.devicewatch.quick.QuickMetricFormatter
import org.jarsi.devicewatch.quick.quickMetricLabels
import org.jarsi.devicewatch.quick.quickStats
import org.jarsi.devicewatch.textLocale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The monitor's figures as Android draws them in the shade when the phone and the app are both
 * in Arabic. A line that reached the system unmarked took its direction from its first letter
 * there, and the Arabic line of figures begins with "RAM": it was laid out from the left, the
 * first figure at the left end, where an Arabic line has its last (external review round 7).
 * The notification is built by the app's own code, inflated by Android's own template and
 * laid out by the real text engine; what is asked is where the figures stand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "ar-rEG-ldrtl-w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationDrawnOrderTest {

    private val context: Context get() = LatinDigits.context(ApplicationProvider.getApplicationContext())

    @Before
    fun theAppsDigits() {
        LatinDigits.applyToDefault()
    }

    /** Memory first, then storage: in Arabic "RAM 5.1 / 7.6 GB · التخزين 84 / 128 GB". */
    private fun figures(): LiveNotificationText {
        val labels = quickMetricLabels(context)
        val stats = quickStats(usedRamGb = 5.1, totalRamGb = 7.6, usedStorageGb = 84.2, totalStorageGb = 128.0)
        val readings = listOf(QuickMetric.MEMORY, QuickMetric.STORAGE).map { QuickMetricFormatter.read(it, stats, null, false, labels) }
        return liveNotificationText(readings, labels)!!
    }

    /** The notification's title as the shade's own template lays it out on this phone. */
    @Suppress("DEPRECATION") // the template is asked for the way the system builds it
    private fun drawnTitle(notification: Notification): TextView {
        val parent = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val views = Notification.Builder.recoverBuilder(context, notification).createContentView()
        val root = views.apply(context, parent)
        parent.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
        return root.findViewById(android.R.id.title)
    }

    @Test
    fun `the phone and the app are both in Arabic, and the line of figures begins with a Latin name`() {
        assertThat(Bidi.isRtl(phoneLocale(context))).isTrue()
        assertThat(textLocale(context).language).isEqualTo("ar")
        assertThat(figures().title.trimStart { it.code in 0x2066..0x2069 }).startsWith("RAM")
    }

    @Test
    fun `in the shade the first figure stands on the right and the second on its left`() {
        val title = drawnTitle(MonitorNotifications(context).live(figures()))
        val shown = title.text.toString()
        val first = shown.indexOf("RAM")
        val second = shown.indexOf(quickMetricLabels(context).names.getValue(QuickMetric.STORAGE).shortName)
        assertThat(first).isAtLeast(0)
        assertThat(second).isGreaterThan(first)
        assertThat(title.layout.lineCount).isEqualTo(1)

        val firstAt = title.layout.getPrimaryHorizontal(first)
        val secondAt = title.layout.getPrimaryHorizontal(second)
        assertWithMessage("RAM at $firstAt, the storage figure at $secondAt, in a line ${title.layout.width} wide")
            .that(firstAt).isGreaterThan(secondAt)
    }

    // What the fix is: the line carries the direction of the language it is written in.
    @Test
    fun `the line goes to the system as a right-to-left piece although the phone reads that way too`() {
        val notification = MonitorNotifications(context).live(figures())

        for (key in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_BIG_TEXT)) {
            val line = notification.extras.getCharSequence(key).toString()
            assertWithMessage(key).that(line.first().code).isEqualTo(0x2067)
            assertWithMessage(key).that(line.last().code).isEqualTo(0x2069)
        }
    }
}
