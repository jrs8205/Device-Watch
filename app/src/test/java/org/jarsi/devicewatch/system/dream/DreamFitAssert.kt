package org.jarsi.devicewatch.system.dream

import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertWithMessage

/**
 * Fails if any text on the screen is cut off: wider than its box, wrapped onto a second
 * line, taller than its box, or placed partly outside the screen. Texts tagged
 * [DREAM_TRUNCATES_TAG] are meant to end in an ellipsis and are left out. [case] names the
 * style and state in the message. Real text measuring is needed, so the test runs in the
 * native graphics mode.
 *
 * "Wider than its box" compares the width the text needs on one line with the width it
 * got. TextLayoutResult.didOverflowWidth cannot be used for this: for a text that may wrap
 * it is true whenever the text is narrower than the room it was offered.
 */
internal fun ComposeContentTestRule.assertNoTextCutOff(case: String) {
    val root = onRoot().fetchSemanticsNode()
    val texts = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    )
    val count = texts.fetchSemanticsNodes().size
    assertWithMessage("$case: no text was drawn").that(count).isGreaterThan(0)
    for (index in 0 until count) {
        val node = texts[index]
        val semantics = node.fetchSemanticsNode()
        if (semantics.config.getOrNull(SemanticsProperties.TestTag) == DREAM_TRUNCATES_TAG) continue
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        val needed = layout.multiParagraph.maxIntrinsicWidth
        assertWithMessage(
            "$case: \"$text\" is cut off: it needs $needed px on one line and has ${layout.size.width} px " +
                "(at most ${layout.layoutInput.constraints.maxWidth} px were on offer)"
        )
            .that(needed)
            .isAtMost(layout.size.width + 0.5f)
        assertWithMessage("$case: \"$text\" wraps onto ${layout.lineCount} lines")
            .that(layout.lineCount)
            .isEqualTo(1)
        assertWithMessage("$case: \"$text\" is taller than its box (${layout.size.height} px)")
            .that(layout.didOverflowHeight)
            .isFalse()
        // Where the text is drawn, not where it was laid out: a block drawn smaller to fit
        // a low screen (see ClockOverBattery) keeps its laid-out size.
        val coordinates = semantics.layoutInfo.coordinates
        val drawn = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        val left = drawn.left
        val top = drawn.top
        val right = drawn.right
        val bottom = drawn.bottom
        assertWithMessage(
            "$case: \"$text\" lies outside the ${root.size.width} x ${root.size.height} screen " +
                "(left $left, top $top, right $right, bottom $bottom)"
        )
            .that(left >= -0.5f && top >= -0.5f && right <= root.size.width + 0.5f && bottom <= root.size.height + 0.5f)
            .isTrue()
    }
}

/**
 * Fails if a text on the screen carries a digit other than 0 to 9: the fit is measured with
 * the digits the app prints, and a test set up without them would measure other widths.
 */
internal fun ComposeContentTestRule.assertOnlyDigitsZeroToNine(case: String) {
    val texts = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    )
    for (index in 0 until texts.fetchSemanticsNodes().size) {
        val layouts = mutableListOf<TextLayoutResult>()
        texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single().layoutInput.text.text
        assertWithMessage("$case: \"$text\" has digits other than 0 to 9")
            .that(text.any { it.isDigit() && it !in '0'..'9' })
            .isFalse()
    }
}
