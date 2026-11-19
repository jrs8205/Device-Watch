package org.jarsi.devicewatch.presentation.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
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
import org.jarsi.devicewatch.SourceXml
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Texts the app did not write: an app's name, a package name, a notification's title and
 * text, the kernel's own names. They come in any script and often begin with a number. Laid
 * out from the right like the Arabic page around them, a code "123 456" read "456 123" and
 * "7 Minute Workout" read "Minute Workout 7". They are laid out as a value is: by their own
 * first letter, and from the left when they have none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "ar-rEG-w360dp-h780dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutsideTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        // The row is one thing to tap, so its texts are merged into it: each is asked for by itself.
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun assertReadsFromTheLeft(text: String) {
        val layout = layoutOf(text)
        assertWithMessage(text).that(layout.layoutInput.style.textDirection).isEqualTo(TextDirection.ContentOrLtr)
        assertWithMessage(text).that(layout.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Ltr)
    }

    @Test
    fun `a notification's app, title and text in the log read by their own direction`() {
        val entry = NotificationLogEntry(0L, "com.example.bank", "7 Minute Bank", "Code 3 / 4", "123 456")
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NotificationLogRow(entry)
            }
        }

        assertReadsFromTheLeft("7 Minute Bank")
        assertReadsFromTheLeft("Code 3 / 4")
        assertReadsFromTheLeft("123 456")
        // The app's name fills the line beside the time: it still stands at the side where the
        // page's reading starts. (The title and the text are as wide as their words, and their
        // column puts them there.)
        val app = layoutOf("7 Minute Bank")
        assertThat(app.getLineRight(0)).isWithin(1f).of(app.size.width.toFloat())
    }

    @Test
    fun `a notification in Arabic still reads from the right`() {
        val words = String(charArrayOf(Char(0x631), Char(0x633), Char(0x627), Char(0x644), Char(0x629)))
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NotificationLogRow(NotificationLogEntry(0L, "com.example", "App", words, "Text"))
            }
        }

        assertThat(layoutOf(words).getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Rtl)
    }

    // A wake-up source is often named by an address: "a600000.ssusb", "18800000.usb".
    @Test
    fun `the kernel's own name for a thing reads from the left`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KernelTechnical("18800000.usb")
            }
        }

        assertReadsFromTheLeft("18800000.usb")
    }

    /**
     * The other places are single texts deep inside whole pages. This reads the pages' source:
     * every text that is given an app's name, a package name or a notification's words must
     * ask for the value style. It would not catch a new kind of outside text under another
     * name; it does catch the next list of apps.
     */
    @Test
    fun `every text that shows an app's name or a notification's words asks for its direction by content`() {
        val folder = SourceXml.source("java/org/jarsi/devicewatch/presentation/ui")
        val problems = folder.listFiles().orEmpty().filter { it.extension == "kt" }.flatMap { file ->
            val source = file.readText()
            OUTSIDE_TEXT.findAll(source).mapNotNull { match ->
                val start = source.lastIndexOf("Text(", match.range.first)
                val call = source.substring(start, closingParen(source, start + "Text".length) + 1)
                val line = source.substring(0, match.range.first).count { it == LINE_END } + 1
                "${file.name}:$line ${match.value}".takeIf { "valueTextStyle()" !in call }
            }.toList()
        }
        assertWithMessage(problems.joinToString(LINE_END.toString(), prefix = LINE_END.toString())).that(problems).isEmpty()
    }

    /** The index of the bracket that closes the one opened at [open]. */
    private fun closingParen(source: String, open: Int): Int {
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return index
            }
        }
        error("no closing bracket")
    }

    private companion object {
        const val LINE_END = '\n'

        /** `text = app.label`, `text = detail.packageName`, `text = entry.title`, `text = segment.label ?: …`. */
        val OUTSIDE_TEXT = Regex("""text = (?:\w+\.)(?:label|appLabel|packageName)\b|text = entry\.(?:title|text)\b""")
    }
}
