package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

class KernelSnapshotSourceTest {

    private val sysfs = FakeSysfsRoot.pixel8a()
    private val settings = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT)
    private var screenOn = true
    private val source = ShellKernelSnapshotSource(RootOnlyShell(settings, sysfs) { screenOn })

    private fun id(name: String) = CpuPolicyId.of(name)!!

    @Test
    fun `fake sysfs reads the lowest of the request, the heat limit and the hardware`() {
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)
        sysfs.halLimit("policy8", 2_294_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        sysfs.throttle("policy8", 1_745_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(1_745_000L)
        sysfs.coolDown("policy8")
        sysfs.halReset("policy8")
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)
    }

    @Test
    fun `with root on it reads and parses the whole tree`() {
        val snapshot = source.read()!!
        assertThat(snapshot.policies.map { it.id.name }).containsExactly("policy0", "policy4", "policy8").inOrder()
        assertThat(snapshot.policies.last().steps).hasSize(15)
        assertThat(sysfs.commands).containsExactly(KernelCommands.FULL_READ)
    }

    @Test
    fun `the cpu read gives the clusters with their steps and the heat, and asks for nothing else`() {
        sysfs.throttle("policy4", 1_836_000L)
        val snapshot = source.readCpu()!!
        assertThat(snapshot.policies.map { it.id.name }).containsExactly("policy0", "policy4", "policy8").inOrder()
        assertThat(snapshot.policies.last().steps).hasSize(15)
        assertThat(snapshot.throttledPolicies).containsExactly(id("policy4"))
        assertThat(snapshot.thermalZones).isEmpty()
        assertThat(sysfs.commands).containsExactly(KernelCommands.CPU_READ)
    }

    @Test
    fun `heat shows in the snapshot`() {
        sysfs.throttle("policy8", 1_745_000L)
        val snapshot = source.read()!!
        assertThat(snapshot.cpuThrottled).isTrue()
        assertThat(snapshot.policies.last().scalingMaxKHz).isEqualTo(1_745_000L)
    }

    @Test
    fun `without root nothing is read`() {
        settings.access = PrivilegedAccess.SHIZUKU
        assertThat(source.read()).isNull()
        assertThat(source.readMaxFreqs(listOf(id("policy8")))).isNull()
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `with the screen off nothing is read`() {
        screenOn = false
        assertThat(source.read()).isNull()
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `a shell that does not answer reads as nothing`() {
        sysfs.failCommands = true
        assertThat(source.read()).isNull()
    }

    @Test
    fun `reads back just the limits asked for`() {
        sysfs.halLimit("policy4", 1_836_000L)
        assertThat(source.readMaxFreqs(listOf(id("policy4"), id("policy8"))))
            .containsExactly(id("policy4"), 1_836_000L, id("policy8"), 2_914_000L)
    }

    @Test
    fun `asking for no limits asks the shell nothing`() {
        assertThat(source.readMaxFreqs(emptyList())).isEmpty()
        assertThat(sysfs.commands).isEmpty()
    }
}
