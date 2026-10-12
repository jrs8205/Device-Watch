package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuProfileRulesTest {

    private val p4 = CpuPolicyId.of("policy4")!!
    private val p8 = CpuPolicyId.of("policy8")!!

    @Test
    fun `a limit read back as written took`() {
        assertThat(CpuProfileRules.acceptance(2_294_000L, 2_294_000L, lowered = false)).isEqualTo(Acceptance.EXACT)
        assertThat(CpuProfileRules.acceptance(2_294_000L, 2_294_000L, lowered = true)).isEqualTo(Acceptance.EXACT)
    }

    @Test
    fun `a lower read back is accepted only while heat or battery saver explain it`() {
        assertThat(CpuProfileRules.acceptance(2_294_000L, 1_745_000L, lowered = true)).isEqualTo(Acceptance.HELD_LOWER)
        assertThat(CpuProfileRules.acceptance(2_294_000L, 1_745_000L, lowered = false)).isEqualTo(Acceptance.REJECTED)
    }

    @Test
    fun `a higher or missing read back means the write did not take`() {
        assertThat(CpuProfileRules.acceptance(2_294_000L, 2_914_000L, lowered = true)).isEqualTo(Acceptance.REJECTED)
        assertThat(CpuProfileRules.acceptance(2_294_000L, null, lowered = false)).isEqualTo(Acceptance.REJECTED)
    }

    @Test
    fun `the original is what the phone held`() {
        assertThat(CpuProfileRules.captureOriginal(2_914_000L, 2_914_000L, lowered = false))
            .isEqualTo(CapturedOriginal(2_914_000L, assumed = false))
        // A maker's own lower limit is kept as the phone's own.
        assertThat(CpuProfileRules.captureOriginal(2_600_000L, 2_914_000L, lowered = false))
            .isEqualTo(CapturedOriginal(2_600_000L, assumed = false))
    }

    @Test
    fun `a limit read while heat or battery saver lowers it is taken to be the maximum`() {
        // Written back as "stock", it would keep the throttle for good.
        assertThat(CpuProfileRules.captureOriginal(1_745_000L, 2_914_000L, lowered = true))
            .isEqualTo(CapturedOriginal(2_914_000L, assumed = true))
    }

    @Test
    fun `nothing is captured from a cluster that cannot be read`() {
        assertThat(CpuProfileRules.captureOriginal(null, 2_914_000L, lowered = false)).isNull()
        assertThat(CpuProfileRules.captureOriginal(2_914_000L, null, lowered = false)).isNull()
    }

    @Test
    fun `only clusters raised above their held limit are set again`() {
        val held = mapOf(p4 to 1_836_000L, p8 to 2_294_000L)
        assertThat(CpuProfileRules.toReapply(held, mapOf(p4 to 1_836_000L, p8 to 2_914_000L))).containsExactly(p8)
        // Lower is heat or Android's own lower limit: never raised by the app.
        assertThat(CpuProfileRules.toReapply(held, mapOf(p4 to 1_418_000L, p8 to 1_745_000L))).isEmpty()
        // A cluster that answered nothing (offline) is left alone.
        assertThat(CpuProfileRules.toReapply(held, mapOf(p4 to 2_367_000L))).containsExactly(p4)
    }

    private val holding = CpuProfileState(
        active = CpuProfile.BALANCED,
        held = mapOf(p8 to 2_294_000L),
        custom = mapOf(p8 to 1_745_000L),
        originals = mapOf(p8 to 2_914_000L),
        originalsAssumed = setOf(p8),
        pending = PendingApply(CpuProfile.POWER_SAVING, mapOf(p8 to 1_745_000L), mapOf(p8 to 2_294_000L)),
        overrideCount = 4,
        reapplyFailures = 1,
        bootCount = 37,
        notice = CpuProfileNotice.KEPT_UNTIL_RESTART,
    )

    @Test
    fun `the first run only remembers the boot count`() {
        assertThat(CpuProfileRules.onBootCount(CpuProfileState(), 37)).isEqualTo(CpuProfileState(bootCount = 37))
    }

    @Test
    fun `the same boot, such as after an app update, changes nothing`() {
        assertThat(CpuProfileRules.onBootCount(holding, 37)).isEqualTo(holding)
    }

    @Test
    fun `an unknown boot count changes nothing`() {
        assertThat(CpuProfileRules.onBootCount(holding, null)).isEqualTo(holding)
    }

    @Test
    fun `a restart without apply-after-restart returns to stock and says so`() {
        val after = CpuProfileRules.onBootCount(holding, 38)
        assertThat(after.active).isEqualTo(CpuProfile.STOCK)
        assertThat(after.notice).isEqualTo(CpuProfileNotice.RESET_BY_RESTART)
        assertThat(after.held).isEmpty()
        assertThat(after.originals).isEmpty()
        assertThat(after.originalsAssumed).isEmpty()
        assertThat(after.pending).isNull()
        assertThat(after.overrideCount).isEqualTo(0)
        assertThat(after.reapplyFailures).isEqualTo(0)
        assertThat(after.bootApplyDue).isFalse()
        assertThat(after.bootCount).isEqualTo(38)
        assertThat(after.custom).isEqualTo(holding.custom)
    }

    @Test
    fun `a restart with apply-after-restart keeps the profile and applies it again`() {
        val after = CpuProfileRules.onBootCount(holding.copy(applyAfterRestart = true), 38)
        assertThat(after.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(after.bootApplyDue).isTrue()
        assertThat(after.held).isEmpty()
        assertThat(after.originals).isEmpty()
        assertThat(after.notice).isNull()
    }

    @Test
    fun `a restart on stock just clears what the old boot held`() {
        val after = CpuProfileRules.onBootCount(holding.copy(active = CpuProfile.STOCK), 38)
        assertThat(after.active).isEqualTo(CpuProfile.STOCK)
        assertThat(after.notice).isNull()
        assertThat(after.bootApplyDue).isFalse()
        assertThat(after.held).isEmpty()
    }
}
