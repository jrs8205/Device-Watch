package org.jarsi.devicewatch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CpuProfileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DeviceLocalCpuProfileStore(context)
    private val p0 = CpuPolicyId.of("policy0")!!
    private val p4 = CpuPolicyId.of("policy4")!!
    private val p8 = CpuPolicyId.of("policy8")!!

    @Test
    fun `nothing saved reads as stock`() {
        assertThat(store.load()).isEqualTo(CpuProfileState())
    }

    @Test
    fun `every field survives a save and a load`() {
        val state = CpuProfileState(
            active = CpuProfile.CUSTOM,
            held = mapOf(p4 to 1_836_000L, p8 to 2_294_000L),
            custom = mapOf(p0 to 955_000L, p8 to 2_294_000L),
            originals = mapOf(p0 to 1_704_000L, p4 to 2_367_000L, p8 to 2_914_000L),
            originalsAssumed = setOf(p8),
            pending = PendingApply(
                profile = CpuProfile.CUSTOM,
                writes = mapOf(p8 to 1_745_000L, p0 to 1_704_000L),
                previousHeld = mapOf(p8 to 2_294_000L, p0 to 955_000L),
                held = mapOf(p8 to 1_745_000L, p4 to 1_836_000L),
                custom = mapOf(p8 to 1_745_000L),
            ),
            applyAfterRestart = true,
            bootProfile = CpuProfile.BALANCED,
            overrideCount = 3,
            reapplyFailures = 2,
            bootCount = 41,
            notice = CpuProfileNotice.ANDROID_KEEPS_CHANGING,
        )
        store.save(state)
        assertThat(DeviceLocalCpuProfileStore(context).load()).isEqualTo(state)
    }

    @Test
    fun `saving again clears what is no longer there`() {
        store.save(CpuProfileState(active = CpuProfile.BALANCED, held = mapOf(p8 to 2_294_000L), notice = CpuProfileNotice.KEPT_UNTIL_RESTART,
            pending = PendingApply(CpuProfile.STOCK, mapOf(p8 to 2_914_000L), emptyMap(), emptyMap(), emptyMap())))
        store.save(CpuProfileState(bootCount = 5))
        assertThat(store.load()).isEqualTo(CpuProfileState(bootCount = 5))
    }

    @Test
    fun `the profile stays on this phone, out of the backed-up settings`() {
        store.save(CpuProfileState(active = CpuProfile.BALANCED, held = mapOf(p8 to 2_294_000L)))
        val backedUp = context.getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(backedUp.all.keys.filter { it.startsWith("cpu_") }).isEmpty()
        val local = context.getSharedPreferences("device_local", Context.MODE_PRIVATE)
        assertThat(local.all.keys).contains("cpu_active")
    }

    @Test
    fun `the codec reads back what it wrote`() {
        val caps = mapOf(p4 to 1_836_000L, p8 to 2_294_000L)
        assertThat(CpuCapsCodec.decode(CpuCapsCodec.encode(caps))).isEqualTo(caps)
        assertThat(CpuCapsCodec.decode(CpuCapsCodec.encode(emptyMap()))).isEmpty()
    }

    @Test
    fun `the codec drops anything that is not a policy and a sane speed`() {
        val decoded = CpuCapsCodec.decode("policyX=1;policy4=0;policy8=99999999999;policy0=955000;junk;=;policy4/../x=5")
        assertThat(decoded).containsExactly(p0, 955_000L)
    }

    @Test
    fun `an unknown saved profile or notice reads as the default`() {
        context.getSharedPreferences("device_local", Context.MODE_PRIVATE).edit()
            .putString("cpu_active", "TURBO").putString("cpu_notice", "SOMETHING").commit()
        val state = store.load()
        assertThat(state.active).isEqualTo(CpuProfile.STOCK)
        assertThat(state.notice).isNull()
    }
}
