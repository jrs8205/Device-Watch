package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KernelSysfsParserTest {

    private val pixel = KernelSysfsParser.parse(KernelFixtures.PIXEL_8A_FULL)

    private fun id(name: String) = CpuPolicyId.of(name)!!

    @Test
    fun `policy ids accept only cpufreq policy names`() {
        assertThat(CpuPolicyId.of("policy0")?.name).isEqualTo("policy0")
        assertThat(CpuPolicyId.of("policy12")?.maxFreqPath)
            .isEqualTo("/sys/devices/system/cpu/cpufreq/policy12/scaling_max_freq")
        assertThat(CpuPolicyId.of("policyX")).isNull()
        assertThat(CpuPolicyId.of("policy4/../../x")).isNull()
        assertThat(CpuPolicyId.of("policy4'; reboot")).isNull()
        assertThat(CpuPolicyId.of("policy12345")).isNull()
    }

    @Test
    fun `reads the pixel's three clusters in order of their top speed`() {
        assertThat(pixel.policies.map { it.id.name }).containsExactly("policy0", "policy4", "policy8").inOrder()
        val big = pixel.policies.last()
        assertThat(big.relatedCpus).containsExactly(8)
        assertThat(big.cpuinfoMinKHz).isEqualTo(500_000L)
        assertThat(big.cpuinfoMaxKHz).isEqualTo(2_914_000L)
        assertThat(big.scalingMaxKHz).isEqualTo(2_914_000L)
        assertThat(big.governor).isEqualTo("sched_pixel")
        assertThat(big.availableGovernors).containsExactly("sched_pixel", "conservative", "powersave", "performance", "schedutil").inOrder()
        assertThat(big.driver).isEqualTo("exynos_cpufreq")
        assertThat(big.online).isTrue()
    }

    @Test
    fun `steps come from the kernel's frequency list, sorted`() {
        val big = pixel.policies.single { it.id == id("policy8") }
        assertThat(big.stepsSource).isEqualTo(StepsSource.AVAILABLE)
        assertThat(big.steps).hasSize(15)
        assertThat(big.steps.first()).isEqualTo(500_000L)
        assertThat(big.steps.last()).isEqualTo(2_914_000L)
        assertThat(big.steps).isInOrder()
    }

    @Test
    fun `steps fall back to time_in_state when the list is missing`() {
        val snapshot = KernelSysfsParser.parse(KernelFixtures.TWO_CLUSTERS_TIME_IN_STATE)
        val big = snapshot.policies.single { it.id == id("policy6") }
        assertThat(big.stepsSource).isEqualTo(StepsSource.TIME_IN_STATE)
        assertThat(big.steps).containsExactly(652_800L, 1_209_600L, 1_708_800L, 2_400_000L).inOrder()
    }

    @Test
    fun `a policy with neither list nor statistics has no steps`() {
        val only = KernelSysfsParser.parse(KernelFixtures.ONE_CLUSTER_NO_STEPS).policies.single()
        assertThat(only.stepsSource).isEqualTo(StepsSource.NONE)
        assertThat(only.steps).isEmpty()
    }

    @Test
    fun `steps outside the hardware range are dropped`() {
        val output = """
            /sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq:300000
            /sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:1800000
            /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1800000
            /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies:200000 300000 1800000 1800000 2500000
        """.trimIndent()
        assertThat(KernelSysfsParser.parse(output).policies.single().steps).containsExactly(300_000L, 1_800_000L).inOrder()
    }

    @Test
    fun `a policy whose limits cannot be read is offline`() {
        val output = """
            /sys/devices/system/cpu/cpufreq/policy0/related_cpus:0 1 2 3
            /sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:1800000
            /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1800000
            /sys/devices/system/cpu/cpufreq/policy4/related_cpus:4 5 6 7
        """.trimIndent()
        val policies = KernelSysfsParser.parse(output).policies
        assertThat(policies.single { it.id == id("policy0") }.online).isTrue()
        assertThat(policies.single { it.id == id("policy4") }.online).isFalse()
    }

    @Test
    fun `reads the mali gpu and picks its active power policy`() {
        val gpu = pixel.gpu!!
        assertThat(gpu.curKHz).isEqualTo(150_000L)
        assertThat(gpu.maxKHz).isEqualTo(890_000L)
        assertThat(gpu.minKHz).isEqualTo(150_000L)
        assertThat(gpu.steps.first()).isEqualTo(150_000L)
        assertThat(gpu.steps).isInOrder()
        assertThat(gpu.powerPolicy).isEqualTo("adaptive")
    }

    @Test
    fun `reads an adreno gpu in hertz as kilohertz`() {
        val gpu = KernelSysfsParser.parse(KernelFixtures.TWO_CLUSTERS_TIME_IN_STATE).gpu!!
        assertThat(gpu.curKHz).isEqualTo(257_000L)
        assertThat(gpu.maxKHz).isEqualTo(840_000L)
        assertThat(gpu.minKHz).isEqualTo(257_000L)
        assertThat(gpu.steps).containsExactly(257_000L, 490_000L, 670_000L, 840_000L).inOrder()
    }

    @Test
    fun `active choice is the bracketed word`() {
        assertThat(KernelSysfsParser.activeChoice("coarse_demand [adaptive] always_on ")).isEqualTo("adaptive")
        assertThat(KernelSysfsParser.activeChoice("[mq-deadline] kyber bfq none")).isEqualTo("mq-deadline")
        assertThat(KernelSysfsParser.activeChoice("none")).isEqualTo("none")
        assertThat(KernelSysfsParser.activeChoice("  ")).isNull()
    }

    @Test
    fun `reads cooling devices and notices no throttling on a cool phone`() {
        val cpuFreq0 = pixel.coolingDevices.single { it.type == "thermal-cpufreq-0" }
        assertThat(cpuFreq0.index).isEqualTo(8)
        assertThat(cpuFreq0.cur).isEqualTo(0)
        assertThat(cpuFreq0.max).isEqualTo(9)
        assertThat(pixel.coolingDevices).hasSize(26)
        assertThat(pixel.cpuThrottled).isFalse()
    }

    @Test
    fun `a cpufreq cooling device in use means the processor is throttled`() {
        val throttled = KernelSysfsParser.parse(
            KernelFixtures.PIXEL_8A_FULL.replace(
                "/sys/class/thermal/cooling_device11/cur_state:0",
                "/sys/class/thermal/cooling_device11/cur_state:3",
            )
        )
        assertThat(throttled.cpuThrottled).isTrue()
    }

    @Test
    fun `only real temperatures are kept, in degrees`() {
        val big = pixel.thermalZones.single { it.type == "BIG" }
        assertThat(big.celsius).isEqualTo(26.0)
        val types = pixel.thermalZones.map { it.type }
        assertThat(types).containsNoneOf("battery_cycle", "soc", "mdis_morepower", "thb_hda")
        assertThat(types).contains("battery")
        assertThat(pixel.thermalZones.map { it.index }).isInOrder()
    }

    @Test
    fun `reads zram size, algorithm and compression`() {
        val zram = pixel.zram!!
        assertThat(zram.disksizeBytes).isEqualTo(3_970_453_504L)
        assertThat(zram.algorithm).isEqualTo("lz77eh")
        assertThat(zram.origBytes).isEqualTo(2_486_063_104L)
        assertThat(zram.comprBytes).isEqualTo(694_901_841L)
        assertThat(zram.memUsedBytes).isEqualTo(727_855_104L)
        assertThat(zram.ratio!!).isWithin(0.01).of(3.58)
    }

    @Test
    fun `zram with nothing compressed has no ratio`() {
        val zram = KernelSysfsParser.parse("/sys/block/zram0/mm_stat:0 0 0 0 0 0 0 0 0").zram!!
        assertThat(zram.ratio).isNull()
    }

    @Test
    fun `reads kernel release, selinux and io schedulers`() {
        assertThat(pixel.kernelRelease).isEqualTo("6.1.162-android14-11-g9dd05ae3a1de-ab16261587")
        assertThat(pixel.selinux).isEqualTo(SelinuxMode.ENFORCING)
        assertThat(pixel.ioSchedulers).containsEntry("sda", "mq-deadline")
        assertThat(pixel.ioSchedulers).hasSize(4)
        assertThat(KernelSysfsParser.parse("/sys/fs/selinux/enforce:0").selinux).isEqualTo(SelinuxMode.PERMISSIVE)
    }

    @Test
    fun `lines from paths outside the allowlist are ignored`() {
        val output = """
            /sys/devices/system/cpu/cpufreq/policyX/scaling_max_freq:100
            /data/local/tmp/policy0/scaling_max_freq:100
            /sys/devices/system/cpu/cpufreq/policy0/../policy4/scaling_max_freq:100
            garbage without a colon
            /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1704000
            /sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:1704000
        """.trimIndent()
        val policies = KernelSysfsParser.parse(output).policies
        assertThat(policies.map { it.id.name }).containsExactly("policy0")
        assertThat(policies.single().scalingMaxKHz).isEqualTo(1_704_000L)
    }

    @Test
    fun `a value containing a colon stays whole`() {
        val snapshot = KernelSysfsParser.parse("/proc/sys/kernel/osrelease:6.1:weird")
        assertThat(snapshot.kernelRelease).isEqualTo("6.1:weird")
    }

    @Test
    fun `max freqs reads only scaling max lines`() {
        val output = """
            /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq:1836000
            /sys/devices/system/cpu/cpufreq/policy8/scaling_max_freq:2294000
            /sys/devices/system/cpu/cpufreq/policy8/cpuinfo_max_freq:2914000
            /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:junk
        """.trimIndent()
        assertThat(KernelSysfsParser.maxFreqs(output))
            .containsExactly(id("policy4"), 1_836_000L, id("policy8"), 2_294_000L)
    }

    @Test
    fun `empty output is an empty snapshot`() {
        val snapshot = KernelSysfsParser.parse("")
        assertThat(snapshot.policies).isEmpty()
        assertThat(snapshot.gpu).isNull()
        assertThat(snapshot.zram).isNull()
        assertThat(snapshot.selinux).isNull()
        assertThat(snapshot.cpuThrottled).isFalse()
    }

    @Test
    fun `mainline cpufreq cooling devices count as processor heat`() {
        val output = """
            /sys/class/thermal/cooling_device0/type:cpufreq-cpu4
            /sys/class/thermal/cooling_device0/cur_state:3
            /sys/class/thermal/cooling_device0/max_state:10
        """.trimIndent()
        assertThat(KernelSysfsParser.parse(output).cpuThrottled).isTrue()
    }
}
