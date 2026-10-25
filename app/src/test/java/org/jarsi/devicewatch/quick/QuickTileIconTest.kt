package org.jarsi.devicewatch.quick

import android.app.Application
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickTileIconTest {

    @Test
    fun `a figure becomes a square bitmap icon`() {
        val icon = quickTileValueIcon("100%")

        assertThat(icon.type).isEqualTo(Icon.TYPE_BITMAP)
        val drawable = icon.loadDrawable(ApplicationProvider.getApplicationContext())!!
        assertThat(drawable.intrinsicWidth).isEqualTo(drawable.intrinsicHeight)
    }
}
