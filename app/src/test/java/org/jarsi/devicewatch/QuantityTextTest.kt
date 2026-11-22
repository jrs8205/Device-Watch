package org.jarsi.devicewatch

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A counted text ("1 time", "3 times") has a form for each way its language counts, and
 * Android picks the form by the phone's language. The texts are not always in that language:
 * a phone in Tibetan or Japanese gets the English ones, and since neither language has a
 * singular it read "1 times"; a phone in Russian, which counts 21 like 1, read "21 time" and
 * called 21 wake-ups "once". [quantityText] picks the form by the language the texts are in.
 */
@RunWith(RobolectricTestRunner::class)
class QuantityTextTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun on(phone: String): Context =
        app.createConfigurationContext(Configuration().apply { setLocales(LocaleList.forLanguageTags(phone)) })

    private val plurals: Map<String, Int> =
        R.plurals::class.java.fields.associate { it.name to it.getInt(null) }

    // That this runtime has the fault when left alone: the tests below cannot pass by accident.
    @Test
    fun `left alone, Android counts the English texts the way the phone's language counts`() {
        assertThat(on("ja-JP").resources.getQuantityString(R.plurals.privacy_times, 1, 1)).isEqualTo("1 times")
        assertThat(on("bo-CN").resources.getQuantityString(R.plurals.privacy_times, 1, 1)).isEqualTo("1 times")
        assertThat(on("ru-RU").resources.getQuantityString(R.plurals.privacy_times, 21, 21)).isEqualTo("21 time")
        assertThat(on("ru-RU").resources.getQuantityString(R.plurals.kernel_wakeups, 21, 21)).isEqualTo("once")
        assertThat(on("fr-FR").resources.getQuantityString(R.plurals.privacy_band_apps, 0, 0)).isEqualTo("0 app")
    }

    @Test
    fun `one is counted as one on a phone whose language has no singular`() {
        for (phone in listOf("ja-JP", "ko-KR", "bo-CN", "th-TH", "vi-VN", "id-ID", "zh-TW")) {
            assertWithMessage(phone).that(quantityText(on(phone), R.plurals.privacy_times, 1, 1)).isEqualTo("1 time")
            assertWithMessage(phone).that(quantityText(on(phone), R.plurals.privacy_times, 2, 2)).isEqualTo("2 times")
        }
    }

    @Test
    fun `a number the phone's language counts like one is not called one`() {
        assertThat(quantityText(on("ru-RU"), R.plurals.privacy_times, 21, 21)).isEqualTo("21 times")
        assertThat(quantityText(on("ru-RU"), R.plurals.kernel_wakeups, 21, 21)).isEqualTo("21 times")
        assertThat(quantityText(on("ru-RU"), R.plurals.comparison_days, 31, 31)).isEqualTo("First 31 days of each period")
        assertThat(quantityText(on("fr-FR"), R.plurals.privacy_band_apps, 0, 0)).isEqualTo("0 apps")
    }

    // Every counted text there is, on phones whose languages count in every way that differs
    // from the app's four, alone and before one of the app's languages on the phone's list:
    // the form is the one a phone in the texts' own language is shown.
    @Test
    fun `every counted text takes the form its own language asks for, on any phone`() {
        val phones = listOf(
            "ja-JP", "bo-CN", "ru-RU", "fr-FR", "pt-BR", "pl-PL", "he-IL", "cy-GB", "lv-LV", "ga-IE", "sl-SI",
            "fr-FR,ar-EG", "ru-RU,ar-EG", "ja-JP,fi-FI", "ru-RU,zh-CN", "ja-JP,ar-EG",
        )
        val wrong = mutableListOf<String>()
        for (phone in phones) {
            val context = on(phone)
            val own = on(textLocale(context).toLanguageTag())
            for ((name, id) in plurals) for (count in COUNTS) {
                val expected = own.resources.getQuantityText(id, count).toString()
                val actual = quantityPattern(context, id, count)
                if (actual != expected) wrong += "$phone: $name for $count is \"$actual\", not \"$expected\""
            }
        }

        assertThat(plurals.size).isAtLeast(17)
        assertWithMessage(wrong.joinToString("\n", prefix = "\n")).that(wrong).isEmpty()
    }

    // A guard: where the phone's language is the texts', nothing changes.
    @Test
    fun `on a phone in one of the app's languages a counted text is what Android gives`() {
        for (phone in listOf("en-US", "en-GB", "fi-FI", "zh-CN", "ar-EG", "ar-SA", "ar-EG-u-nu-latn")) {
            val context = on(phone)
            for ((name, id) in plurals) for (count in COUNTS) {
                assertWithMessage("$phone: $name for $count")
                    .that(quantityPattern(context, id, count))
                    .isEqualTo(context.resources.getQuantityText(id, count).toString())
            }
        }
    }

    // The form is the texts' language's; the number in it is still written as the phone
    // writes numbers, by the same rule as everywhere else: a numbering system the user chose
    // stands.
    @Test
    fun `the number in a counted text is written as the phone writes it`() {
        val thai = Char(0x0E51) // the Thai digit one
        assertThat(on("th-TH-u-nu-thai").resources.getQuantityString(R.plurals.privacy_times, 1, 1)).isEqualTo("$thai times")
        assertThat(quantityText(on("th-TH-u-nu-thai"), R.plurals.privacy_times, 1, 1)).isEqualTo("$thai time")
        assertThat(quantityText(on("th-TH"), R.plurals.cpu_cores_value, 8, 8, "arm64")).isEqualTo("8 cores (arm64)")
    }

    // Read from the source, as DateRouteGuardTest reads its own: a counted text asked of
    // Android directly anywhere else would be counted by the phone's language again.
    @Test
    fun `no counted text is asked of Android anywhere but in one file`() {
        val main = listOf(File("src/main/java/org/jarsi/devicewatch"), File("app/src/main/java/org/jarsi/devicewatch"))
            .first { it.isDirectory }
        val files = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val askers = files.filter { DIRECT.containsMatchIn(it.readText()) }.map { it.relativeTo(main).invariantSeparatorsPath }

        assertThat(files.size).isAtLeast(100)
        assertThat(askers).containsExactly("QuantityText.kt")
    }

    private companion object {
        val COUNTS = listOf(0, 1, 2, 3, 5, 11, 20, 21, 22, 100, 101, 102, 1000000)

        /** Android's own ways to a counted text, in Compose and outside it. */
        val DIRECT = Regex("""pluralStringResource\(|getQuantityString\(|getQuantityText\(""")
    }
}
