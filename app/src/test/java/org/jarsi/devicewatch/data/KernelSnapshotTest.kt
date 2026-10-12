package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KernelSnapshotTest {

    private fun policy(name: String, cpus: List<Int>, max: Long) = CpuPolicySnapshot(
        id = CpuPolicyId.of(name)!!,
        relatedCpus = cpus,
        cpuinfoMinKHz = 300_000,
        cpuinfoMaxKHz = max,
        scalingMinKHz = 300_000,
        scalingMaxKHz = max,
        curKHz = max,
        governor = "schedutil",
        availableGovernors = emptyList(),
        driver = null,
        steps = emptyList(),
        stepsSource = StepsSource.NONE,
    )

    private val p0 = CpuPolicyId.of("policy0")!!
    private val p4 = CpuPolicyId.of("policy4")!!
    private val p8 = CpuPolicyId.of("policy8")!!

    private val pixel8a = listOf(
        policy("policy0", listOf(0, 1, 2, 3), 1_704_000),
        policy("policy4", listOf(4, 5, 6, 7), 2_367_000),
        policy("policy8", listOf(8), 2_914_000),
    )

    private fun snapshot(policies: List<CpuPolicySnapshot>, vararg cooling: CoolingDevice) = KernelSnapshot(
        policies = policies,
        gpu = null,
        coolingDevices = cooling.toList(),
        thermalZones = emptyList(),
        zram = null,
        kernelRelease = null,
        selinux = null,
        ioSchedulers = emptyMap(),
    )

    @Test
    fun `pixel's cooling devices name the clusters in core order`() {
        val snapshot = snapshot(
            pixel8a,
            CoolingDevice(0, "thermal-cpufreq-0", 0, 10),
            CoolingDevice(1, "thermal-cpufreq-1", 2, 10),
            CoolingDevice(2, "thermal-cpufreq-2", 0, 10),
        )
        assertThat(snapshot.throttledPolicies).containsExactly(p4)
        assertThat(snapshot.cpuThrottled).isTrue()
    }

    @Test
    fun `core order is the clusters' first core, not the page's speed order`() {
        // Listed biggest first, as a page might sort them; the numbering still follows the cores.
        val snapshot = snapshot(
            pixel8a.reversed(),
            CoolingDevice(0, "thermal-cpufreq-0", 1, 10),
            CoolingDevice(1, "thermal-cpufreq-1", 0, 10),
            CoolingDevice(2, "thermal-cpufreq-2", 0, 10),
        )
        assertThat(snapshot.throttledPolicies).containsExactly(p0)
    }

    @Test
    fun `mainline cooling devices name the cluster by its first core`() {
        val snapshot = snapshot(
            pixel8a,
            CoolingDevice(3, "cpufreq-cpu8", 1, 10),
            CoolingDevice(4, "cpufreq-cpu0", 0, 10),
        )
        assertThat(snapshot.throttledPolicies).containsExactly(p8)
    }

    @Test
    fun `a cooling device names its cluster the same way for the page and the profile`() {
        val devices = listOf(
            CoolingDevice(0, "thermal-cpufreq-0", 0, 10),
            CoolingDevice(1, "thermal-cpufreq-1", 0, 10),
            CoolingDevice(2, "thermal-cpufreq-2", 0, 10),
            CoolingDevice(5, "cpufreq-cpu8", 0, 10),
            CoolingDevice(7, "thermal-gpufreq-0", 0, 10),
        )
        val snapshot = snapshot(pixel8a, *devices.toTypedArray())
        assertThat(snapshot.policyFor(devices[1])).isEqualTo(p4)
        assertThat(snapshot.policyFor(devices[3])).isEqualTo(p8)
        assertThat(snapshot.policyFor(CoolingDevice(6, "thermal-cpufreq-7", 0, 10))).isNull()
        assertThat(snapshot.policyFor(devices[4])).isNull()
    }

    @Test
    fun `pixel's numbering is trusted only when every cluster was read`() {
        // The little cluster is off and missing from the read; the devices keep their boot-time numbers.
        val snapshot = snapshot(
            pixel8a.drop(1),
            CoolingDevice(0, "thermal-cpufreq-0", 0, 10),
            CoolingDevice(1, "thermal-cpufreq-1", 2, 10),
            CoolingDevice(2, "thermal-cpufreq-2", 0, 10),
        )
        assertThat(snapshot.throttledPolicies).isEmpty()
        assertThat(snapshot.policyFor(CoolingDevice(1, "thermal-cpufreq-1", 2, 10))).isNull()
        assertThat(snapshot.cpuThrottled).isTrue()
    }

    @Test
    fun `a cooling device at rest throttles nothing`() {
        val snapshot = snapshot(pixel8a, CoolingDevice(0, "thermal-cpufreq-1", 0, 10))
        assertThat(snapshot.throttledPolicies).isEmpty()
        assertThat(snapshot.cpuThrottled).isFalse()
    }

    @Test
    fun `a cooling device that names no known cluster counts as heat for none of them`() {
        val snapshot = snapshot(pixel8a, CoolingDevice(0, "thermal-cpufreq-7", 3, 10), CoolingDevice(1, "cpufreq-cpu20", 3, 10))
        assertThat(snapshot.throttledPolicies).isEmpty()
        // The phone is hot all the same.
        assertThat(snapshot.cpuThrottled).isTrue()
    }

    @Test
    fun `a cooling device that is not a processor's is not heat on a cluster`() {
        val snapshot = snapshot(pixel8a, CoolingDevice(0, "gpu-cooling", 4, 10))
        assertThat(snapshot.throttledPolicies).isEmpty()
        assertThat(snapshot.cpuThrottled).isFalse()
    }
}
