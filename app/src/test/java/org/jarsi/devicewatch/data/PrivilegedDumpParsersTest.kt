package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** Lines as a Galaxy Z Flip 4 (Android 16) and a Pixel 8a (Android 17) printed them. */
class PrivilegedDumpParsersTest {

    private val pixelThermal = """
        IsStatusOverride: false
        Thermal Status: 0
        Cached temperatures:
        	Temperature{mValue=65.0, mType=0, mName=LITTLE, mStatus=0}
        	Temperature{mValue=59.000004, mType=1, mName=G3D, mStatus=0}
        HAL Ready: true
        HAL connection:
        	ThermalHAL AIDL 3  connected: yes
        Current temperatures from HAL:
        	Temperature{mValue=34.0, mType=1, mName=G3D, mStatus=0}
        	Temperature{mValue=44.000004, mType=0, mName=BIG, mStatus=0}
        	Temperature{mValue=46.000004, mType=0, mName=LITTLE, mStatus=0}
        	Temperature{mValue=0.0, mType=4, mName=VIRTUAL-USB-UI, mStatus=0}
        	Temperature{mValue=-0.98, mType=-1, mName=USB-MINUS-CHARGER, mStatus=0}
        	Temperature{mValue=45.000004, mType=0, mName=MID, mStatus=0}
        	Temperature{mValue=29.300001, mType=2, mName=battery, mStatus=0}
        	Temperature{mValue=29.347502, mType=3, mName=VIRTUAL-SKIN, mStatus=0}
        Current cooling devices from HAL:
        	CoolingDevice{mValue=0, mType=1, mName=dc_icl}
    """.trimIndent()

    @Test
    fun `the thermal service's current readings are read, not its stale cache`() {
        val readings = ThermalServiceParser.parse(pixelThermal)

        assertThat(readings).hasSize(8)
        assertThat(readings.first()).isEqualTo(HalTemperature(type = 1, name = "G3D", celsius = 34.0))
        // The cached 65 °C of an earlier moment is not among them.
        assertThat(readings.map { it.celsius }).doesNotContain(65.0)
    }

    @Test
    fun `the hottest sensor of a kind stands for it`() {
        val readings = ThermalServiceParser.parse(pixelThermal)

        assertThat(ThermalServiceParser.hottest(readings, ThermalServiceParser.TYPE_CPU)!!).isWithin(0.001).of(46.0)
        assertThat(ThermalServiceParser.hottest(readings, ThermalServiceParser.TYPE_GPU)).isEqualTo(34.0)
        assertThat(ThermalServiceParser.hottest(readings, ThermalServiceParser.TYPE_SKIN)!!).isWithin(0.001).of(29.3475)
        // An unused sensor reads zero: no temperature rather than a frozen phone.
        assertThat(ThermalServiceParser.hottest(readings, 4)).isNull()
        assertThat(ThermalServiceParser.hottest(readings, 9)).isNull()
    }

    @Test
    fun `without current readings the cached ones do`() {
        val cachedOnly = """
            Cached temperatures:
            	Temperature{mValue=31.4, mType=0, mName=AP, mStatus=0}
            	Temperature{mValue=32.4, mType=3, mName=SKIN, mStatus=0}
            HAL Ready: false
        """.trimIndent()

        assertThat(ThermalServiceParser.parse(cachedOnly)).containsExactly(
            HalTemperature(0, "AP", 31.4),
            HalTemperature(3, "SKIN", 32.4),
        ).inOrder()
        assertThat(ThermalServiceParser.parse("Permission Denial")).isEmpty()
    }

    private val samsungBattery = """
        Current Battery Service state:
          AC powered: false
          level: 96
          temperature: 304
        LLB CAL: 20221116
        LLB MAN:
        LLB CURRENT: YEAR2026M10D2
        LLB DIFF: 202
          FEATURE_SAVE_BATTERY_CYCLE: true
          battery FirstUseDate: [20230603]
          mSavedBatteryAsoc: [98]
          mSavedBatteryUsage: [50904]
          mSavedFullStatusDuration: unsupported
    """.trimIndent()

    @Test
    fun `Samsung's battery service tells the battery's wear, cycles and dates`() {
        assertThat(SamsungBatteryParser.parse(samsungBattery)).isEqualTo(
            SamsungBattery(
                healthPercent = 98,
                // Samsung counts discharged percent: 50904 % is 509 full cycles.
                cycles = 509,
                firstUse = LocalDate.of(2023, 6, 3),
                manufactured = LocalDate.of(2022, 11, 16),
            )
        )
    }

    @Test
    fun `another maker's battery service has nothing of the kind`() {
        val pixel = """
            Current Battery Service state:
              AC powered: false
              level: 93
              Charging state: 1
        """.trimIndent()

        assertThat(SamsungBatteryParser.parse(pixel)).isNull()
    }

    @Test
    fun `values Samsung marks unsupported are left out, not shown as zero`() {
        val partial = """
              battery FirstUseDate: [00000000]
              mSavedBatteryAsoc: [-1]
              mSavedBatteryUsage: [12345]
        """.trimIndent()

        assertThat(SamsungBatteryParser.parse(partial))
            .isEqualTo(SamsungBattery(healthPercent = null, cycles = 123, firstUse = null, manufactured = null))
    }

    @Test
    fun `graphics load is read whatever the driver's notation`() {
        // Adreno's gpu_busy_percentage, Mali's utilization, and Exynos' gpu_busy.
        assertThat(PrivilegedReadings.gpuLoadPercent("12 %")).isEqualTo(12)
        assertThat(PrivilegedReadings.gpuLoadPercent("7\n")).isEqualTo(7)
        assertThat(PrivilegedReadings.gpuLoadPercent("45%")).isEqualTo(45)
        assertThat(PrivilegedReadings.gpuLoadPercent("")).isNull()
        assertThat(PrivilegedReadings.gpuLoadPercent("250")).isNull()
        assertThat(PrivilegedReadings.gpuLoadPercent(null)).isNull()
    }

    @Test
    fun `a Pixel's battery dates are seconds since 1970`() {
        assertThat(PrivilegedReadings.epochSecondsDate("1717804800\n")).isEqualTo(LocalDate.of(2024, 6, 8))
        assertThat(PrivilegedReadings.epochSecondsDate("1709164800")).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(PrivilegedReadings.epochSecondsDate("0")).isNull()
        assertThat(PrivilegedReadings.epochSecondsDate("n/a")).isNull()
        // Garbage in the node must not throw: this many seconds is past any date.
        assertThat(PrivilegedReadings.epochSecondsDate("9223372036854775807")).isNull()
        assertThat(PrivilegedReadings.epochSecondsDate("86400")).isNull()
    }

    @Test
    fun `a Samsung manufacture date wins over the calibration date`() {
        val both = "LLB CAL: 20221116\nLLB MAN: 20221020\n"

        assertThat(SamsungBatteryParser.parse(both)!!.manufactured).isEqualTo(LocalDate.of(2022, 10, 20))
    }
}
