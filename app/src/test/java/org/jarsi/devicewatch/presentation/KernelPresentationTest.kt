package org.jarsi.devicewatch.presentation

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.data.CpuPolicyId
import org.jarsi.devicewatch.data.CpuProfile
import org.jarsi.devicewatch.data.CpuProfileStatus
import org.jarsi.devicewatch.data.KernelFixtures
import org.jarsi.devicewatch.data.KernelSysfsParser
import org.junit.Test
import java.util.Locale

class KernelPresentationTest {

    private val pixel = KernelSysfsParser.parse(KernelFixtures.PIXEL_8A_FULL)
    private fun id(name: String) = CpuPolicyId.of(name)!!

    @Test
    fun `speeds read in gigahertz from one gigahertz up, in the locale's decimal style`() {
        assertThat(freqText(2_294_000L, Locale.forLanguageTag("fi"))).isEqualTo("2,29 GHz")
        assertThat(freqText(2_294_000L, Locale.ENGLISH)).isEqualTo("2.29 GHz")
        assertThat(freqText(1_000_000L, Locale.ENGLISH)).isEqualTo("1.00 GHz")
        assertThat(freqText(820_000L, Locale.ENGLISH)).isEqualTo("820 MHz")
    }

    @Test
    fun `a limit is shown over the maximum`() {
        assertThat(capOverMaxText(2_294_000L, 2_914_000L, Locale.forLanguageTag("fi"))).isEqualTo("2,29 / 2,91 GHz")
    }

    @Test
    fun `clusters are little, middle and big by their top speed`() {
        assertThat(clusterRoles(pixel.policies))
            .containsExactly(id("policy0"), ClusterRole.LITTLE, id("policy4"), ClusterRole.MIDDLE, id("policy8"), ClusterRole.BIG)
        val two = KernelSysfsParser.parse(KernelFixtures.TWO_CLUSTERS_TIME_IN_STATE).policies
        assertThat(clusterRoles(two)).containsExactly(id("policy0"), ClusterRole.LITTLE, id("policy6"), ClusterRole.BIG)
        val one = KernelSysfsParser.parse(KernelFixtures.ONE_CLUSTER_NO_STEPS).policies
        assertThat(clusterRoles(one)).containsExactly(id("policy0"), ClusterRole.ONLY)
    }

    @Test
    fun `a cluster with its cores off keeps the others' places`() {
        val bigOff = pixel.policies.map {
            if (it.id == id("policy8")) it.copy(relatedCpus = emptyList(), cpuinfoMaxKHz = null, scalingMaxKHz = null) else it
        }
        assertThat(clusterRoles(bigOff))
            .containsExactly(id("policy0"), ClusterRole.LITTLE, id("policy4"), ClusterRole.BIG, id("policy8"), ClusterRole.OFFLINE)
    }

    @Test
    fun `core ranges read as a span when they are consecutive`() {
        assertThat(cpuRangeText(listOf(0, 1, 2, 3))).isEqualTo("0–3")
        assertThat(cpuRangeText(listOf(8))).isEqualTo("8")
        assertThat(cpuRangeText(listOf(0, 2, 5))).isEqualTo("0, 2, 5")
        assertThat(cpuRangeText(emptyList())).isEqualTo("")
    }

    @Test
    fun `cooling devices are told apart by what they slow down`() {
        assertThat(CoolingKind.of("thermal-cpufreq-2")).isEqualTo(CoolingKind.Cpu)
        assertThat(CoolingKind.of("thermal-gpufreq-0")).isEqualTo(CoolingKind.Gpu)
        assertThat(CoolingKind.of("fcc")).isEqualTo(CoolingKind.ChargingCurrent)
        assertThat(CoolingKind.of("dc_icl")).isEqualTo(CoolingKind.ChargingCurrent)
        assertThat(CoolingKind.of("BIG-tj")).isEqualTo(CoolingKind.Other)
        assertThat(CoolingKind.of("thermal-cpufreq-x")).isEqualTo(CoolingKind.Other)
    }

    @Test
    fun `the summary names the profile, the big cluster's limit and the heat`() {
        val summary = KernelSummary.from(pixel, CpuProfileStatus(active = CpuProfile.BALANCED))
        assertThat(summary.profile).isEqualTo(CpuProfile.BALANCED)
        assertThat(summary.bigCapKHz).isEqualTo(2_914_000L)
        assertThat(summary.bigMaxKHz).isEqualTo(2_914_000L)
        assertThat(summary.throttled).isFalse()
        assertThat(summary.supported).isTrue()
    }

    @Test
    fun `a kernel without speed steps is not supported`() {
        val summary = KernelSummary.from(KernelSysfsParser.parse(KernelFixtures.ONE_CLUSTER_NO_STEPS), CpuProfileStatus())
        assertThat(summary.supported).isFalse()
    }

    @Test
    fun `known sensors get a plain name, others keep their own`() {
        assertThat(thermalZoneLabelRes("BIG")).isNotNull()
        assertThat(thermalZoneLabelRes("battery")).isNotNull()
        assertThat(thermalZoneLabelRes("rffe_therm")).isNull()
    }

    @Test
    fun `mainline cooling names are told apart too`() {
        assertThat(CoolingKind.of("cpufreq-cpu4")).isEqualTo(CoolingKind.Cpu)
        assertThat(CoolingKind.of("devfreq-1f000000.mali")).isEqualTo(CoolingKind.Gpu)
        assertThat(CoolingKind.of("devfreq-gpu")).isEqualTo(CoolingKind.Gpu)
        assertThat(CoolingKind.of("devfreq-ddr")).isEqualTo(CoolingKind.Other)
    }
}
