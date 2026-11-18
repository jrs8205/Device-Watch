package org.jarsi.devicewatch.presentation.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Short lengths of time used to be put together in code as "34 min" and "1 min 27 s",
 * whatever the language. They are the language's own texts now.
 */
@RunWith(RobolectricTestRunner::class)
class DurationTextTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Arabic and nothing more. A text's own number prints with the language's digits here,
     * which tells a text taken from the resources from one put together in code.
     */
    private val arabic: Context
        get() = app.createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag("ar-EG")) })

    @Test
    fun `under an hour a length is the language's minutes text`() {
        assertThat(durationText(arabic, 34 * 60_000L)).isEqualTo(arabic.getString(R.string.duration_minutes, 34))
        assertThat(durationText(app, 34 * 60_000L)).isEqualTo("34 min")
    }

    @Test
    fun `from an hour up a length is hours and minutes as before`() {
        assertThat(durationText(app, 134 * 60_000L)).isEqualTo("2h 14m")
    }

    @Test
    fun `a hold of minutes and seconds is the language's text`() {
        assertThat(holdDurationText(arabic, 87_000L))
            .isEqualTo(arabic.getString(R.string.privacy_duration_minutes, 1, 27))
        assertThat(holdDurationText(app, 87_000L)).isEqualTo("1 min 27 s")
    }

    @Test
    fun `a hold of seconds is the language's text`() {
        assertThat(holdDurationText(arabic, 6_000L)).isEqualTo(arabic.getString(R.string.duration_seconds, 6))
        assertThat(holdDurationText(app, 6_000L)).isEqualTo("6 s")
    }
}
