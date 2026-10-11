package org.jarsi.devicewatch.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceNameTest {

    @Test
    fun `android's device name is shown as it is`() {
        assertThat(screensaverDeviceName("Pixel 8a", "Pixel 8a", "Google")).isEqualTo("Pixel 8a")
        assertThat(screensaverDeviceName("Pixel 10 Pro", "Pixel 10 Pro", "Google")).isEqualTo("Pixel 10 Pro")
        assertThat(screensaverDeviceName("Galaxy Z Flip4", "SM-F721B", "samsung")).isEqualTo("Galaxy Z Flip4")
    }

    @Test
    fun `a leading maker name is dropped when a product name follows`() {
        assertThat(screensaverDeviceName("Google Pixel 8a", null, "Google")).isEqualTo("Pixel 8a")
        assertThat(screensaverDeviceName("Samsung Galaxy S24", null, "samsung")).isEqualTo("Galaxy S24")
    }

    @Test
    fun `the maker name stays when it is part of the product name`() {
        assertThat(screensaverDeviceName("OnePlus 12", null, "OnePlus")).isEqualTo("OnePlus 12")
        assertThat(screensaverDeviceName("Xiaomi 14 Ultra", null, "Xiaomi")).isEqualTo("Xiaomi 14 Ultra")
        assertThat(screensaverDeviceName("Nothing Phone (2)", null, "Nothing")).isEqualTo("Nothing Phone (2)")
    }

    @Test
    fun `spacing is tidied`() {
        assertThat(screensaverDeviceName("  Pixel   8a ", null, "Google")).isEqualTo("Pixel 8a")
    }

    @Test
    fun `a long name is cut with an ellipsis`() {
        val name = screensaverDeviceName("Jarin työpuhelin Pixel 8a Pro", null, "Google")
        assertThat(name).isEqualTo("Jarin työpuhelin Pixel…")
        assertThat(name!!.length).isAtMost(24)
    }

    @Test
    fun `without a device name the model is used`() {
        assertThat(screensaverDeviceName(null, "Pixel 8a", "Google")).isEqualTo("Pixel 8a")
        assertThat(screensaverDeviceName("  ", "moto g54 5G", "motorola")).isEqualTo("moto g54 5G")
    }

    @Test
    fun `a bare model code is never shown`() {
        assertThat(screensaverDeviceName(null, "SM-F721B", "samsung")).isNull()
        assertThat(screensaverDeviceName("", "CPH2581", "OPPO")).isNull()
        assertThat(screensaverDeviceName(null, "M2101K6G", "Xiaomi")).isNull()
    }

    @Test
    fun `a device name that is only the model code is left out too`() {
        // Plain Android names the device after Build.MODEL when the maker sets no name.
        assertThat(screensaverDeviceName("CPH2581", "CPH2581", "OPPO")).isNull()
        assertThat(screensaverDeviceName("FP5", "FP5", "Fairphone")).isEqualTo("FP5")
    }

    @Test
    fun `an emoji at the cut is never split in half`() {
        // The emoji's two halves sit at the 23rd and 24th characters, across the cut.
        val name = screensaverDeviceName("Jarin työpuhelin Pixel📱 8a", null, "Google")
        assertThat(name).isEqualTo("Jarin työpuhelin Pixel…")
    }

    @Test
    fun `no name at all gives nothing`() {
        assertThat(screensaverDeviceName(null, null, null)).isNull()
        assertThat(screensaverDeviceName("", " ", "Google")).isNull()
    }
}
