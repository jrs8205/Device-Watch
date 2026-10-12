package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuProfilePlannerTest {

    private val pixel = KernelSysfsParser.parse(KernelFixtures.PIXEL_8A_FULL).policies
    private val p0 = id("policy0")
    private val p4 = id("policy4")
    private val p8 = id("policy8")
    private val pixelOriginals = mapOf(p0 to 1_704_000L, p4 to 2_367_000L, p8 to 2_914_000L)

    private fun id(name: String) = CpuPolicyId.of(name)!!

    private fun plan(
        profile: CpuProfile,
        policies: List<CpuPolicySnapshot> = pixel,
        custom: Map<CpuPolicyId, Long> = emptyMap(),
        originals: Map<CpuPolicyId, Long> = pixelOriginals,
        held: Map<CpuPolicyId, Long> = emptyMap(),
    ) = CpuProfilePlanner.plan(policies, profile, custom, originals, held)

    @Test
    fun `power saving holds the middle and big clusters to 60 percent, snapped down`() {
        val plan = plan(CpuProfile.POWER_SAVING)
        assertThat(plan.caps).containsExactly(p4, 1_418_000L, p8, 1_745_000L)
        assertThat(plan.restores).isEmpty()
        assertThat(plan.unsupported).isEmpty()
    }

    @Test
    fun `balanced holds them to 80 percent`() {
        assertThat(plan(CpuProfile.BALANCED).caps).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `the little cluster is never limited by a preset`() {
        assertThat(plan(CpuProfile.POWER_SAVING).caps).doesNotContainKey(p0)
        assertThat(CpuProfilePlanner.littleIds(pixel)).containsExactly(p0)
    }

    @Test
    fun `a little cluster held by a custom limit is restored by a preset`() {
        val plan = plan(CpuProfile.BALANCED, held = mapOf(p0 to 820_000L, p8 to 1_557_000L))
        assertThat(plan.restores).containsExactly(p0, 1_704_000L)
        assertThat(plan.caps).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
        assertThat(plan.writes).containsExactly(p0, 1_704_000L, p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `stock restores every held cluster to its original and holds nothing`() {
        val plan = plan(CpuProfile.STOCK, held = mapOf(p4 to 1_418_000L, p8 to 1_745_000L))
        assertThat(plan.caps).isEmpty()
        assertThat(plan.restores).containsExactly(p4, 2_367_000L, p8, 2_914_000L)
    }

    @Test
    fun `stock with nothing held writes nothing`() {
        assertThat(plan(CpuProfile.STOCK).writes).isEmpty()
    }

    @Test
    fun `a held cluster without a saved original goes back to its hardware maximum`() {
        val plan = plan(CpuProfile.STOCK, originals = emptyMap(), held = mapOf(p8 to 1_745_000L))
        assertThat(plan.restores).containsExactly(p8, 2_914_000L)
    }

    @Test
    fun `a limit never exceeds the original the phone had`() {
        val plan = plan(CpuProfile.BALANCED, originals = pixelOriginals + (p8 to 2_049_000L))
        assertThat(plan.caps[p8]).isEqualTo(2_049_000L)
    }

    @Test
    fun `custom limits any cluster, the little one too, to a listed step`() {
        val plan = plan(CpuProfile.CUSTOM, custom = mapOf(p0 to 830_000L, p8 to 2_000_000L))
        assertThat(plan.caps).containsExactly(p0, 955_000L, p8, 1_885_000L)
    }

    @Test
    fun `custom at the top step is no limit at all`() {
        val plan = plan(CpuProfile.CUSTOM, custom = mapOf(p8 to 2_914_000L), held = mapOf(p8 to 1_745_000L))
        assertThat(plan.caps).isEmpty()
        assertThat(plan.restores).containsExactly(p8, 2_914_000L)
    }

    @Test
    fun `custom offers only steps from half the maximum up`() {
        val big = pixel.single { it.id == p8 }
        val steps = CpuProfilePlanner.customSteps(big, original = 2_914_000L)
        assertThat(steps.first()).isEqualTo(1_557_000L)
        assertThat(steps.last()).isEqualTo(2_914_000L)
        assertThat(steps).hasSize(11)
        assertThat(CpuProfilePlanner.customSteps(pixel.single { it.id == p0 }, original = null).first()).isEqualTo(955_000L)
    }

    @Test
    fun `a custom value below the range snaps up to its lowest step`() {
        val big = pixel.single { it.id == p8 }
        assertThat(CpuProfilePlanner.snapCustom(big, 500_000L, original = 2_914_000L)).isEqualTo(1_557_000L)
        assertThat(CpuProfilePlanner.snapCustom(big, 2_300_000L, original = 2_914_000L)).isEqualTo(2_294_000L)
    }

    @Test
    fun `custom leaves clusters it was not given, and restores them if held`() {
        val plan = plan(CpuProfile.CUSTOM, custom = mapOf(p8 to 1_745_000L), held = mapOf(p4 to 1_418_000L))
        assertThat(plan.caps).containsExactly(p8, 1_745_000L)
        assertThat(plan.restores).containsExactly(p4, 2_367_000L)
    }

    @Test
    fun `a phone with one cluster has no presets, only custom`() {
        val one = KernelSysfsParser.parse(KernelFixtures.TWO_CLUSTERS_TIME_IN_STATE).policies.take(1)
        assertThat(CpuProfilePlanner.presetsAvailable(one)).isFalse()
        val plan = plan(CpuProfile.BALANCED, policies = one, originals = emptyMap())
        assertThat(plan.caps).isEmpty()
        assertThat(plan.unsupported).containsExactly(id("policy0"))
        assertThat(plan(CpuProfile.CUSTOM, policies = one, originals = emptyMap(), custom = mapOf(id("policy0") to 1_100_000L)).caps)
            .containsExactly(id("policy0"), 1_017_600L)
    }

    @Test
    fun `clusters with the same top speed are all little`() {
        val twins = pixel.take(2).map { it.copy(cpuinfoMaxKHz = 1_704_000L, steps = pixel.first().steps) }
        assertThat(CpuProfilePlanner.littleIds(twins)).containsExactly(p0, p4)
        assertThat(CpuProfilePlanner.presetsAvailable(twins)).isFalse()
    }

    @Test
    fun `a two cluster phone limits its big cluster from the time_in_state steps`() {
        val two = KernelSysfsParser.parse(KernelFixtures.TWO_CLUSTERS_TIME_IN_STATE).policies
        val plan = plan(CpuProfile.BALANCED, policies = two, originals = emptyMap())
        assertThat(plan.caps).containsExactly(id("policy6"), 1_708_800L)
    }

    @Test
    fun `a cluster with no steps cannot be limited`() {
        val noSteps = pixel.map { if (it.id == p8) it.copy(steps = emptyList(), stepsSource = StepsSource.NONE) else it }
        val plan = plan(CpuProfile.BALANCED, policies = noSteps)
        assertThat(plan.unsupported).containsExactly(p8)
        assertThat(plan.caps).containsExactly(p4, 1_836_000L)
    }

    @Test
    fun `an offline cluster is skipped, never written`() {
        val offline = pixel.map { if (it.id == p4) it.copy(scalingMaxKHz = null) else it }
        val plan = plan(CpuProfile.POWER_SAVING, policies = offline, held = mapOf(p4 to 1_418_000L))
        assertThat(plan.skipped).containsExactly(p4)
        assertThat(plan.writes).doesNotContainKey(p4)
        assertThat(plan.caps).containsExactly(p8, 1_745_000L)
    }

    @Test
    fun `every planned limit is a listed step within the hardware range`() {
        for (profile in CpuProfile.entries) {
            for (wanted in listOf(1L, 600_000L, 1_000_000L, 1_700_000L, 2_400_000L, 9_000_000L)) {
                val custom = pixel.associate { it.id to wanted }
                val plan = plan(profile, custom = custom, held = pixelOriginals)
                for ((policyId, cap) in plan.caps) {
                    val policy = pixel.single { it.id == policyId }
                    assertThat(policy.steps).contains(cap)
                    assertThat(cap).isAtMost(policy.cpuinfoMaxKHz!!)
                    assertThat(cap).isAtMost(pixelOriginals.getValue(policyId))
                }
            }
        }
    }
}
