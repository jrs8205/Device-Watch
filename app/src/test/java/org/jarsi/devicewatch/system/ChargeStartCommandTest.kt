package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.nio.file.Files
import org.jarsi.devicewatch.data.TestShell
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * Runs the start command through a real `sh`, with `dumpsys`, `service` and `input` stood in
 * by shell functions, so its own check of the screen is tested as the shell runs it: the
 * command may wait behind another one for seconds, and the check has to happen after that.
 */
class ChargeStartCommandTest {

    private class Run(val output: String, val calls: List<String>)

    private fun run(sdkInt: Int, wakefulness: String): Run {
        val sh = TestShell.path
        assumeNotNull(sh)
        val dir = Files.createTempDirectory("charge-start").toFile()
        try {
            val stubs = """
                export PATH="/usr/bin:${'$'}PATH"
                cd '${dir.absolutePath.replace('\\', '/')}'
                dumpsys() {
                    echo "  mWakefulnessChanging=false"
                    if [ -f woke ]; then echo "  mWakefulness=Awake"; else echo "  mWakefulness=$wakefulness"; fi
                    echo "mWakefulness=1"
                }
                service() { echo "service ${'$'}*" >> calls; echo "Result: Parcel(00000000    '....')"; }
                input() { echo "input ${'$'}*" >> calls; touch woke; }
            """.trimIndent()
            // A file, not `sh -c`: Windows would mangle the quotes in a command-line argument.
            val script = File(dir, "run.sh").apply { writeText(stubs + "\n" + startDreamCommand(sdkInt) + "\n") }
            val process = ProcessBuilder(sh, script.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val calls = File(dir, "calls").takeIf { it.exists() }?.readLines().orEmpty()
            return Run(output, calls)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `android 14 and later ask for the screensaver from a screen that is off`() {
        listOf("Asleep", "Dozing").forEach { state ->
            val run = run(sdkInt = 34, wakefulness = state)
            assertWithMessage(run.output).that(run.calls).containsExactly("service call dreams 1")
            assertThat(dreamCallOutcome(run.output)).isEqualTo(DreamCallOutcome.STARTED)
        }
    }

    @Test
    fun `a screen that came on while the command waited is left alone`() {
        listOf(34, 33).forEach { sdk ->
            listOf("Awake", "Dreaming").forEach { state ->
                val run = run(sdkInt = sdk, wakefulness = state)
                assertWithMessage(run.output).that(run.calls).isEmpty()
                assertThat(dreamCallOutcome(run.output)).isEqualTo(DreamCallOutcome.SCREEN_ON)
            }
        }
    }

    @Test
    fun `before android 14 the screen is woken first, since the dream manager cannot start from sleep`() {
        val run = run(sdkInt = 33, wakefulness = "Dozing")
        assertWithMessage(run.output).that(run.calls).containsExactly("input keyevent KEYCODE_WAKEUP", "service call dreams 1").inOrder()
        assertThat(dreamCallOutcome(run.output)).isEqualTo(DreamCallOutcome.STARTED)
    }
}
