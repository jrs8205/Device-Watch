package org.jarsi.devicewatch

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.SourceXml.android
import org.junit.Test

/**
 * An icon that points somewhere points the other way in a language written from the right.
 * "Open in new" is an arrow leaving a box towards the far corner of the reading direction.
 */
class MirroredIconsTest {

    @Test
    fun `the open icon turns with the reading direction`() {
        assertThat(SourceXml.parse("res/drawable/ic_open_in_new.xml").android("autoMirrored")).isEqualTo("true")
    }
}
