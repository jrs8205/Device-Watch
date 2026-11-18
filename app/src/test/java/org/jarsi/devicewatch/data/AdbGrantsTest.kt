package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdbGrantsTest {

    @Test
    fun `the commands grant the three permissions to the package, in the order the switch names them`() {
        assertThat(AdbGrantCommands.commands("org.jarsi.devicewatch")).containsExactly(
            "adb shell pm grant org.jarsi.devicewatch android.permission.DUMP",
            "adb shell pm grant org.jarsi.devicewatch android.permission.BATTERY_STATS",
            "adb shell pm grant org.jarsi.devicewatch android.permission.PACKAGE_USAGE_STATS",
        ).inOrder()
    }

    @Test
    fun `a dump Android refused says so on its first line`() {
        assertThat(
            DumpDenial.isDenied(
                "Permission Denial: can't dump batterystats from from pid=4292, uid=10221 " +
                    "due to missing android.permission.PACKAGE_USAGE_STATS permission"
            )
        ).isTrue()
        // Blank lines and indentation ahead of it change nothing.
        assertThat(
            DumpDenial.isDenied("\n\n  Permission Denial: can't dump appops from pid=1, uid=2 without permission android.permission.DUMP")
        ).isTrue()
    }

    @Test
    fun `a dump that mentions a denial further down is still a dump`() {
        assertThat(DumpDenial.isDenied("Statistics since last charge:\n  Permission Denial: something an app logged")).isFalse()
        assertThat(DumpDenial.isDenied("IsStatusOverride: false")).isFalse()
        assertThat(DumpDenial.isDenied("")).isFalse()
    }
}
