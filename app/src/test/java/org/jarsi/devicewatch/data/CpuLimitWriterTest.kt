package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

class CpuLimitWriterTest {

    private val sysfs = FakeSysfsRoot.pixel8a()
    private val settings = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT)
    private val writer = CpuLimitWriter(RootOnlyShell(settings, sysfs) { true })
    private val p4 = CpuPolicyId.of("policy4")!!
    private val p8 = CpuPolicyId.of("policy8")!!

    @Test
    fun `writes every limit and reads them back in one command`() {
        val readback = writer.write(mapOf(p4 to 1_836_000L, p8 to 2_294_000L))!!
        assertThat(sysfs.commands).hasSize(1)
        assertThat(sysfs.writes).containsExactly("policy4" to 1_836_000L, "policy8" to 2_294_000L)
        assertThat(readback.effective).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
        assertThat(readback.throttled).isEmpty()
    }

    @Test
    fun `heat shows in the read back, on the cluster it holds`() {
        sysfs.throttle("policy8", 1_557_000L)
        val readback = writer.write(mapOf(p8 to 2_294_000L))!!
        assertThat(readback.effective).containsExactly(p8, 1_557_000L)
        assertThat(readback.throttled).containsExactly(p8)
    }

    @Test
    fun `heat on another cluster is not heat on the one written`() {
        sysfs.throttle("policy4", 1_836_000L)
        val readback = writer.write(mapOf(p8 to 2_294_000L))!!
        assertThat(readback.throttled).containsExactly(p4)
    }

    @Test
    fun `a refused write reads back the old value`() {
        sysfs.rejectWrites += "policy8"
        val readback = writer.write(mapOf(p4 to 1_836_000L, p8 to 2_294_000L))!!
        assertThat(readback.effective).containsExactly(p4, 1_836_000L, p8, 2_914_000L)
    }

    @Test
    fun `no answer is no read back`() {
        sysfs.loseAnswers = true
        assertThat(writer.write(mapOf(p8 to 2_294_000L))).isNull()
    }

    @Test
    fun `nothing is written without root`() {
        settings.access = PrivilegedAccess.SHIZUKU
        assertThat(writer.write(mapOf(p8 to 2_294_000L))).isNull()
        assertThat(sysfs.commands).isEmpty()
    }
}
