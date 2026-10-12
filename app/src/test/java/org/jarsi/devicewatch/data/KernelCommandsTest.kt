package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class KernelCommandsTest {

    private fun id(name: String) = CpuPolicyId.of(name)!!

    private val allowed = Regex("[A-Za-z0-9_/.'*?^ ;>:-]*")

    @Test
    fun `the full read is one grep over every file the page shows`() {
        val read = KernelCommands.FULL_READ
        assertThat(read).startsWith("grep -s -H '^' ")
        assertThat(read).contains("/proc/sys/kernel/osrelease")
        assertThat(read).contains("/sys/fs/selinux/enforce")
        assertThat(read).contains("/sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq")
        assertThat(read).contains("/sys/devices/system/cpu/cpufreq/policy*/scaling_available_frequencies")
        assertThat(read).contains("/sys/devices/system/cpu/cpufreq/policy*/stats/time_in_state")
        assertThat(read).contains("/sys/class/thermal/cooling_device*/cur_state")
        assertThat(read).contains("/sys/class/thermal/thermal_zone*/temp")
        assertThat(read).contains("/sys/class/misc/mali0/device/power_policy")
        assertThat(read).contains("/sys/class/kgsl/kgsl-3d0/gpuclk")
        assertThat(read).contains("/sys/block/zram0/mm_stat")
        assertThat(read).contains("/sys/block/sd?/queue/scheduler")
        assertThat(read).doesNotContain("{")
        assertThat(allowed.matches(read)).isTrue()
    }

    @Test
    fun `every path in the full read is one the parser accepts`() {
        // A path the parser would skip is a file read for nothing, or a typo.
        val paths = KernelCommands.FULL_READ.removePrefix("grep -s -H '^' ").split(' ')
        val sample = paths.joinToString("\n") { path ->
            path.replace("policy*", "policy0").replace("thermal_zone*", "thermal_zone0")
                .replace("cooling_device*", "cooling_device0").replace("sd?", "sda").replace("mmcblk?", "mmcblk0") + ":1"
        }
        val parsed = KernelSysfsParser.parse(sample)
        assertThat(parsed.policies).hasSize(1)
        assertThat(parsed.kernelRelease).isEqualTo("1")
        assertThat(parsed.ioSchedulers.keys).containsExactly("sda", "mmcblk0")
        assertThat(parsed.zram).isNotNull()
        assertThat(parsed.gpu).isNotNull()
        assertThat(parsed.coolingDevices).hasSize(1)
        assertThat(parsed.thermalZones).hasSize(1)
        assertThat(parsed.selinux).isEqualTo(SelinuxMode.ENFORCING)
        assertThat(paths.size).isEqualTo(paths.toSet().size)
    }

    @Test
    fun `writing limits echoes each value into its own file, then reads them all back`() {
        val command = KernelCommands.writeMaxFreqs(linkedMapOf(id("policy8") to 2_294_000L, id("policy4") to 1_836_000L))
        assertThat(command).isEqualTo(
            "echo 1836000 > '/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq'; " +
                "echo 2294000 > '/sys/devices/system/cpu/cpufreq/policy8/scaling_max_freq'; " +
                "grep -s -H '^' '/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq' " +
                "'/sys/devices/system/cpu/cpufreq/policy8/scaling_max_freq' " +
                "/sys/class/thermal/cooling_device*/type /sys/class/thermal/cooling_device*/cur_state " +
                "/sys/class/thermal/cooling_device*/max_state"
        )
        assertThat(allowed.matches(command)).isTrue()
    }

    @Test
    fun `policies are ordered by number, not by name`() {
        val command = KernelCommands.readMaxFreqs(listOf(id("policy10"), id("policy2")))
        assertThat(command.indexOf("policy2/")).isLessThan(command.indexOf("policy10/"))
    }

    @Test
    fun `reading limits back asks only for those files`() {
        assertThat(KernelCommands.readMaxFreqs(listOf(id("policy4"))))
            .isEqualTo("grep -s -H '^' '/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq'")
    }

    @Test
    fun `values outside a sane speed are refused before any command exists`() {
        assertThrows(IllegalArgumentException::class.java) { KernelCommands.writeMaxFreqs(mapOf(id("policy0") to 0L)) }
        assertThrows(IllegalArgumentException::class.java) { KernelCommands.writeMaxFreqs(mapOf(id("policy0") to -5L)) }
        assertThrows(IllegalArgumentException::class.java) {
            KernelCommands.writeMaxFreqs(mapOf(id("policy0") to KernelCommands.MAX_KHZ + 1))
        }
    }

    @Test
    fun `nothing to write or read is refused`() {
        assertThrows(IllegalArgumentException::class.java) { KernelCommands.writeMaxFreqs(emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { KernelCommands.readMaxFreqs(emptyList()) }
    }
}
