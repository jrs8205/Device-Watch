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
import androidx.test.platform.app.InstrumentationRegistry
import org.jarsi.devicewatch.SourceXml
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.jarsi.devicewatch.data.WakeCategory
import org.jarsi.devicewatch.data.WakeEntry
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

    // The list of what kept the phone awake shows an app's name, or a part of the phone in the
    // app's own words, or the kernel's own name where it says nothing we know. The first and
    // the last are not the app's words; they reached the screen through a variable that the
    // check of the pages' source below did not know.
    @Test
    fun `what kept the phone awake is named by its own direction`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                androidx.compose.foundation.layout.Column {
                    WakeList(
                        org.jarsi.devicewatch.R.string.battery_usage_app_wake_locks,
                        listOf(
                            WakeEntry(owner = "7 Minute Workout", category = null, names = listOf("*job*/x"), millis = 60_000L, count = 2),
                            WakeEntry(owner = null, category = null, names = listOf("18800000.usb"), millis = 30_000L, count = 1),
                            WakeEntry(owner = null, category = WakeCategory.WIFI, names = listOf("wlan_wake"), millis = 20_000L, count = 1),
                        ),
                    )
                }
            }
        }

        assertReadsFromTheLeft("7 Minute Workout")
        assertReadsFromTheLeft("18800000.usb")
        // A part of the phone is named in the app's own words: they read the way the page does.
        val part = InstrumentationRegistry.getInstrumentation().targetContext.getString(wakeCategoryRes(WakeCategory.WIFI))
        assertThat(layoutOf(part).layoutInput.style.textDirection).isNotEqualTo(TextDirection.ContentOrLtr)
    }

    // The small line under a row has two uses that must not share a component: the kernel's
    // own name alone, and a line of the app's own words with such names in it. Given the
    // name's layout, the Arabic line "policy0 · the governor … · the driver …" was read from
    // the left as a whole because it begins with a Latin name.
    @Test
    fun `a small line of the app's own words reads the way the page does`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KernelCaption("policy0 · caption")
            }
        }

        assertThat(layoutOf("policy0 · caption").layoutInput.style.textDirection).isNotEqualTo(TextDirection.ContentOrLtr)
    }

    @Test
    fun `the kernel's-name line is never given a text from the app's own words`() {
        val folder = SourceXml.source("java/org/jarsi/devicewatch/presentation/ui")
        val problems = folder.listFiles().orEmpty().filter { it.extension == "kt" }.flatMap { file ->
            val source = file.readText()
            Regex("""\bKernelTechnical\(""").findAll(source).mapNotNull { match ->
                val call = source.substring(match.range.first, closingParen(source, match.range.last) + 1)
                val line = source.substring(0, match.range.first).count { it == LINE_END } + 1
                "${file.name}:$line $call".takeIf { OWN_WORDS.containsMatchIn(call) }
            }.toList()
        }
        assertWithMessage(problems.joinToString(LINE_END.toString(), prefix = LINE_END.toString())).that(problems).isEmpty()
    }

    // What the user types is not the app's text either. In a field that took the page's
    // direction a typed "123 456" was shown "456 123" (the report's search field did the same).
    @Test
    fun `every field shows what is typed by its own direction`() {
        val folder = SourceXml.source("java/org/jarsi/devicewatch/presentation/ui")
        val fields = folder.listFiles().orEmpty().filter { it.extension == "kt" }.flatMap { file ->
            val source = file.readText()
            Regex("""\b\w*TextField\(""").findAll(source).map { match ->
                val call = source.substring(match.range.first, closingParen(source, match.range.last) + 1)
                val line = source.substring(0, match.range.first).count { it == LINE_END } + 1
                "${file.name}:$line" to call
            }.toList()
        }

        assertThat(fields).isNotEmpty()
        val problems = fields.filter { (_, call) -> "textStyle = typedTextStyle()" !in call }.map { it.first }
        assertWithMessage(problems.joinToString()).that(problems).isEmpty()
    }

    /**
     * The places this round's inventory of the whole app found, where a name the app did not
     * write is put into a caption's place or into one of the app's sentences: there it is
     * handed over as a piece of its own. A list of known places, kept so that none of them
     * goes back; it cannot know a place that is added later.
     */
    @Test
    fun `the names found by the inventory are handed over as pieces of their own`() {
        val places = listOf(
            "presentation/ui/DeviceTab.kt" to "DeviceFact(Bidi.outside(name), value)",
            "presentation/ui/OverviewTab.kt" to "R.string.label_with_sim, mobileLabel, Bidi.outside(simName)",
            "presentation/ui/OverviewTab.kt" to "Bidi.outside(currentStats.cpuAbi)",
            "presentation/ui/KernelPage.kt" to "CoolingKind.Other -> Bidi.outside(device.type)",
            "presentation/ui/KernelPage.kt" to "else Bidi.outside(zone.type)",
            "presentation/ui/KernelSleepSection.kt" to "R.string.wake_process_other, Bidi.outside(kind.process)",
            "presentation/ui/AppDetailSheet.kt" to "is InstallSource.App -> Bidi.outside(source.label)",
            "presentation/ui/PrivacyPage.kt" to "event.tag?.let { tag -> Bidi.outside(tag) }",
            "data/ShellBatteryUsageSource.kt" to "R.string.battery_usage_uid_daemon, Bidi.outside(daemon)",
            "data/SystemStatsRepositoryImpl.kt" to "R.string.camera_lens_line, kind, Bidi.outside(it)",
            "data/SystemStatsRepositoryImpl.kt" to "R.string.exempt_user_and_system, ExtraStatsLogic.namesText(summary.userApps)",
        )
        val problems = places.filter { (file, text) ->
            text !in SourceXml.source("java/org/jarsi/devicewatch/$file").readText()
        }
        assertWithMessage(problems.joinToString(LINE_END.toString())).that(problems).isEmpty()
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

        /** A text taken from the app's resources, or several things joined into a line. */
        val OWN_WORDS = Regex("""stringResource\(|getString\(|pluralStringResource\(|joinToString\(""")
    }
}
