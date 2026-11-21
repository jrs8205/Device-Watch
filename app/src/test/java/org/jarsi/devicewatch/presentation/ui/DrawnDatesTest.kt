package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jarsi.devicewatch.hasOtherDigits
import org.jarsi.devicewatch.presentation.HistoryDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * External review round 9, as the pages draw it: on a phone in Tibetan the History list's
 * "Collected since" line and the day headings of the notification log named the eleventh
 * month in Tibetan digits, beside a day and a year written 0 to 9. The Privacy page's day
 * titles did the same by another formatter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "bo-rCN-w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawnDatesTest {

    @get:Rule
    val compose = createComposeRule()

    /** A day in the eleventh month, long enough ago to be neither today nor yesterday. */
    private val day: LocalDate = LocalDate.of(2025, 11, 20)

    private fun drawnTexts(): List<String> {
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        return (0 until texts.fetchSemanticsNodes().size).map { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.single().layoutInput.text.text
        }
    }

    private fun assertWrittenWithTheAppsDigits(date: String) {
        assertWithMessage(date).that(date.hasOtherDigits()).isFalse()
        assertWithMessage(date).that(Regex("[0-9]+").findAll(date).map { it.value }.toList()).containsExactly("2025", "11", "20")
    }

    @Test
    fun `the History list says since when it was collected in the digits 0 to 9`() {
        val days = listOf(
            HistoryDay(LocalDate.now(), screenTimeMillis = 3_600_000L, unlocks = 12, notifications = 34, boots = 1, charges = 2),
            HistoryDay(day, screenTimeMillis = 7_200_000L, unlocks = 56, notifications = 78, boots = 0, charges = 1),
        )
        compose.setContent { HistoryDayList(days, HistoryMetric.Unlocks) }

        val texts = drawnTexts()
        // The app is not written in Tibetan: the sentence is the English one, the date the phone's.
        val since = texts.single { it.startsWith("Collected since ") }
        assertWrittenWithTheAppsDigits(since.removePrefix("Collected since "))
        assertWithMessage(texts.toString()).that(texts.none { it.hasOtherDigits() }).isTrue()
    }

    @Test
    fun `a day heading of the notification log and a day title of the Privacy page are in them too`() {
        val noon = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.setContent {
            Column {
                BasicText(dayHeaderText(day))
                BasicText(dayTitle(day, noon))
            }
        }

        val texts = drawnTexts()
        assertThat(texts).hasSize(2)
        texts.forEach(::assertWrittenWithTheAppsDigits)
    }
}
