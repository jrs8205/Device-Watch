package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jarsi.devicewatch.presentation.FakeAppSettingsRepository
import org.junit.Test

class CpuProfileControllerTest {

    private class MemoryStore(var state: CpuProfileState = CpuProfileState()) : CpuProfileStore {
        val saved = mutableListOf<CpuProfileState>()

        /**
         * The disk is full, or the preferences file cannot be written: nothing lands.
         * As SharedPreferences does, the in-memory copy still takes the value.
         */
        var failSaves = false

        var loads = 0

        override fun load(): CpuProfileState {
            loads++
            return state
        }

        override fun save(state: CpuProfileState): Boolean {
            this.state = state
            if (failSaves) return false
            saved += state
            return true
        }
    }

    private val sysfs = FakeSysfsRoot.pixel8a()
    private val settings = FakeAppSettingsRepository(access = PrivilegedAccess.ROOT)
    private val store = MemoryStore()
    private var bootNumber = 10
    private var bootId: String? = "boot-10"
    private var bootReads = 0
    private var powerSave = false
    private val gate = RootOnlyShell(settings, sysfs) { true }

    private val p0 = CpuPolicyId.of("policy0")!!
    private val p4 = CpuPolicyId.of("policy4")!!
    private val p8 = CpuPolicyId.of("policy8")!!

    private fun TestScope.controller(dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)) =
        CpuProfileController(
            store = store,
            source = ShellKernelSnapshotSource(gate),
            writer = CpuLimitWriter(gate),
            settings = settings,
            bootId = {
                bootReads++
                bootId
            },
            powerSave = { powerSave },
            dispatcher = dispatcher,
        )

    /** The root gate, with writes that go unanswered after [writesAllowed]: `su` backing off after a timeout. */
    private inner class FlakyWrites(var writesAllowed: Int = Int.MAX_VALUE) : RootCommandShell {
        var writes = 0
        override fun run(command: String, timeoutMillis: Long): String? {
            if (command.startsWith("echo ")) {
                writes++
                if (writes > writesAllowed) return null
            }
            return gate.run(command, timeoutMillis)
        }
    }

    /**
     * The root gate, with write commands numbered from 1: the answer to those in
     * [loseAnswers] is lost after the command ran, and from write [refuseFrom] on
     * the kernel refuses every write to that policy.
     */
    private inner class ScriptedWrites(
        val loseAnswers: Set<Int> = emptySet(),
        val refuseFrom: Map<Int, String> = emptyMap(),
    ) : RootCommandShell {
        var writes = 0
        override fun run(command: String, timeoutMillis: Long): String? {
            if (!command.startsWith("echo ")) return gate.run(command, timeoutMillis)
            writes++
            refuseFrom[writes]?.let { sysfs.rejectWrites += it }
            val answer = gate.run(command, timeoutMillis)
            return if (writes in loseAnswers) null else answer
        }
    }

    private fun TestScope.controllerWriting(shell: RootCommandShell) = CpuProfileController(
        store, ShellKernelSnapshotSource(gate), CpuLimitWriter(shell), settings, { bootId }, { powerSave },
        UnconfinedTestDispatcher(testScheduler),
    )

    /** A restart: a new boot count, and every request in the kernel back to none. */
    private fun reboot() {
        bootNumber++
        bootId = "boot-$bootNumber"
        sysfs.policies.keys.forEach {
            sysfs.halReset(it)
            sysfs.coolDown(it)
        }
    }

    @Test
    fun `balanced holds the middle and big clusters and leaves the little one`() = runTest {
        val controller = controller()
        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Applied)
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(sysfs.writes.map { it.first }).doesNotContain("policy0")
        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(status.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
        assertThat(status.holdsCaps).isTrue()
        assertThat(store.state.originals).containsExactly(p0, 1_704_000L, p4, 2_367_000L, p8, 2_914_000L)
    }

    @Test
    fun `originals are saved before anything is written`() = runTest {
        controller().apply(CpuProfile.BALANCED)
        val firstWithPending = store.saved.first { it.pending != null }
        assertThat(firstWithPending.originals).hasSize(3)
    }

    @Test
    fun `originals are not read again later in the same boot`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        controller.apply(CpuProfile.POWER_SAVING)
        assertThat(store.state.originals[p8]).isEqualTo(2_914_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(1_745_000L)
    }

    @Test
    fun `stock writes the originals back and holds nothing`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.POWER_SAVING)
        assertThat(controller.apply(CpuProfile.STOCK)).isEqualTo(ApplyOutcome.Applied)
        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)
        assertThat(controller.status.value.held).isEmpty()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
    }

    @Test
    fun `stock with nothing held changes nothing`() = runTest {
        assertThat(controller().apply(CpuProfile.STOCK)).isEqualTo(ApplyOutcome.NothingToChange)
        assertThat(sysfs.writes).isEmpty()
    }

    @Test
    fun `custom holds the clusters it is given, the little one too`() = runTest {
        val controller = controller()
        val outcome = controller.apply(CpuProfile.CUSTOM, mapOf(p0 to 1_098_000L, p8 to 2_049_000L))
        assertThat(outcome).isEqualTo(ApplyOutcome.Applied)
        assertThat(sysfs.effective("policy0")).isEqualTo(1_098_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_049_000L)
        assertThat(controller.status.value.custom).containsExactly(p0, 1_098_000L, p8, 2_049_000L)
    }

    @Test
    fun `heat on one cluster does not lift android's lower limit on another`() = runTest {
        val controller = controller()
        // The phone's own limits are known from an earlier profile; then Android holds the big cluster down.
        controller.apply(CpuProfile.BALANCED)
        controller.apply(CpuProfile.STOCK)
        sysfs.throttle("policy4", 1_836_000L)
        sysfs.halLimit("policy8", 1_557_000L)

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.HeldLower)

        // Android's own limit on the big cluster stays; the app's waits for it to let go.
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(1_557_000L)
        assertThat(controller.status.value.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `a limit heat already holds lower is in place`() = runTest {
        sysfs.throttle("policy8", 1_557_000L)
        val controller = controller()
        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.HeldLower)
        assertThat(controller.status.value.held[p8]).isEqualTo(2_294_000L)
        // Read under heat, the big cluster's original is taken to be its maximum.
        assertThat(store.state.originals[p8]).isEqualTo(2_914_000L)
        assertThat(store.state.originalsAssumed).containsExactly(p8)
    }

    @Test
    fun `a refused write rolls the others back and keeps the old profile`() = runTest {
        sysfs.rejectWrites += "policy8"
        val controller = controller()
        val outcome = controller.apply(CpuProfile.BALANCED)
        assertThat(outcome).isEqualTo(ApplyOutcome.NotSupported(setOf(p8)))
        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.STOCK)
        assertThat(status.held).isEmpty()
        assertThat(status.pending).isFalse()
    }

    @Test
    fun `nothing is written without root`() = runTest {
        settings.access = PrivilegedAccess.SHIZUKU
        assertThat(controller().apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.NoRoot)
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `a shell that cannot read is unconfirmed and writes nothing`() = runTest {
        sysfs.failCommands = true
        assertThat(controller().apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)
        assertThat(sysfs.writes).isEmpty()
    }

    @Test
    fun `a lost answer leaves the apply pending, and the next pass settles it as done`() = runTest {
        val controller = controller()
        sysfs.loseAnswers = false
        // The read before the write answers; only the write's answer is lost.
        val writerShell = object : RootCommandShell {
            override fun run(command: String, timeoutMillis: Long): String? =
                gate.run(command, timeoutMillis).takeUnless { command.startsWith("echo ") }
        }
        val lossy = CpuProfileController(store, ShellKernelSnapshotSource(gate), CpuLimitWriter(writerShell), settings, { bootId }, { powerSave }, UnconfinedTestDispatcher(testScheduler))
        assertThat(lossy.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)
        assertThat(lossy.status.value.pending).isTrue()
        assertThat(lossy.status.value.holdsCaps).isTrue()

        controller.enforce()

        val status = controller.status.value
        assertThat(status.pending).isFalse()
        assertThat(status.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(status.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `a pending apply that did not take is rolled back on the next pass`() = runTest {
        sysfs.rejectWrites += "policy8"
        val writerShell = object : RootCommandShell {
            override fun run(command: String, timeoutMillis: Long): String? =
                gate.run(command, timeoutMillis).takeUnless { command.startsWith("echo ") }
        }
        val lossy = CpuProfileController(store, ShellKernelSnapshotSource(gate), CpuLimitWriter(writerShell), settings, { bootId }, { powerSave }, UnconfinedTestDispatcher(testScheduler))
        lossy.apply(CpuProfile.BALANCED)
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)

        val controller = controller()
        controller.enforce()

        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.held).isEmpty()
    }

    @Test
    fun `a limit android raised is set again and counted`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.halReset("policy8")
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)

        controller.enforce()

        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(controller.status.value.overrideCount).isEqualTo(1)
        assertThat(sysfs.writes.last()).isEqualTo("policy8" to 2_294_000L)
    }

    @Test
    fun `heat below the limit is left alone`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.throttle("policy8", 1_557_000L)
        val writes = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.writes).hasSize(writes)
        assertThat(controller.status.value.overrideCount).isEqualTo(0)
    }

    @Test
    fun `android's own lower limit is left alone too`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.halLimit("policy8", 1_852_000L)
        val writes = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.writes).hasSize(writes)
    }

    @Test
    fun `nothing held means nothing to watch`() = runTest {
        controller().enforce()
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `android winning three times in a row lets the profile go`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.rejectWrites += "policy8"
        repeat(CpuProfileRules.MAX_REAPPLY_FAILURES) {
            sysfs.halReset("policy8")
            controller.enforce()
        }
        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.STOCK)
        assertThat(status.notice).isEqualTo(CpuProfileNotice.ANDROID_KEEPS_CHANGING)
        assertThat(status.held).isEmpty()
        // The cluster it could still hold goes back too.
        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
    }

    @Test
    fun `without root the held limits stay recorded until root returns`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        settings.access = PrivilegedAccess.OFF
        val commands = sysfs.commands.size

        controller.enforce()

        assertThat(sysfs.commands).hasSize(commands)
        assertThat(controller.status.value.notice).isEqualTo(CpuProfileNotice.KEPT_UNTIL_RESTART)
        assertThat(controller.status.value.held).hasSize(2)

        settings.access = PrivilegedAccess.ROOT
        controller.enforce()
        assertThat(controller.status.value.notice).isNull()
        assertThat(controller.apply(CpuProfile.STOCK)).isEqualTo(ApplyOutcome.Applied)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)
    }

    @Test
    fun `a restart without apply-after-restart returns to stock without writing`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        reboot()
        val writes = sysfs.writes.size

        controller.enforce()

        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.STOCK)
        assertThat(status.notice).isEqualTo(CpuProfileNotice.RESET_BY_RESTART)
        assertThat(status.held).isEmpty()
        assertThat(sysfs.writes).hasSize(writes)
    }

    @Test
    fun `a restart with apply-after-restart sets the profile again on the first pass`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        controller.apply(CpuProfile.BALANCED)
        reboot()

        controller.enforce()

        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(controller.status.value.held).hasSize(2)
        assertThat(store.state.bootProfile).isNull()
    }

    @Test
    fun `an app update, the same boot, keeps holding`() = runTest {
        controller().apply(CpuProfile.BALANCED)
        // A new process: a new controller over the same saved state.
        val restarted = controller()
        sysfs.halReset("policy8")

        restarted.enforce()

        assertThat(restarted.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
    }

    @Test
    fun `restoring before root goes off writes the originals`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.POWER_SAVING)
        assertThat(controller.restoreStockBeforeRootOff()).isTrue()
        assertThat(sysfs.effective("policy8")).isEqualTo(2_914_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a restore that fails says the limits stay until restart`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.POWER_SAVING)
        sysfs.failCommands = true
        assertThat(controller.restoreStockBeforeRootOff()).isFalse()
        assertThat(controller.status.value.notice).isEqualTo(CpuProfileNotice.KEPT_UNTIL_RESTART)
    }

    @Test
    fun `nothing held needs no restore`() = runTest {
        assertThat(controller().restoreStockBeforeRootOff()).isTrue()
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `root gone asks no shell and notes the limits stay`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        val commands = sysfs.commands.size
        controller.onRootGone()
        assertThat(sysfs.commands).hasSize(commands)
        assertThat(controller.status.value.notice).isEqualTo(CpuProfileNotice.KEPT_UNTIL_RESTART)
    }

    @Test
    fun `apply and a pass at the same time run one after the other`() = runTest {
        val controller = controller(StandardTestDispatcher(testScheduler))
        controller.apply(CpuProfile.BALANCED)
        sysfs.halReset("policy8")
        val apply = async { controller.apply(CpuProfile.POWER_SAVING) }
        val pass = async { controller.enforce() }
        apply.await()
        pass.await()
        // Whatever the order, the pass never brings back the profile just replaced.
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.POWER_SAVING)
        assertThat(sysfs.effective("policy8")).isEqualTo(1_745_000L)
        assertThat(sysfs.effective("policy4")).isEqualTo(1_418_000L)
    }

    @Test
    fun `a write that lands but reads back lower for no known reason is undone with the rest`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.POWER_SAVING)
        sysfs.hiddenLimit("policy8", 2_000_000L)

        val outcome = controller.apply(CpuProfile.BALANCED)

        assertThat(outcome).isEqualTo(ApplyOutcome.NotSupported(setOf(p8)))
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(1_745_000L)
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(1_418_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.POWER_SAVING)
        assertThat(controller.status.value.held).containsExactly(p4, 1_418_000L, p8, 1_745_000L)
    }

    @Test
    fun `a rollback root does not answer stays pending and is settled on the next pass`() = runTest {
        sysfs.rejectWrites += "policy8"
        val flaky = FlakyWrites(writesAllowed = 1)
        val controller = controllerWriting(flaky)

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)
        assertThat(controller.status.value.pending).isTrue()
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)

        flaky.writesAllowed = Int.MAX_VALUE
        controller.enforce()

        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.held).isEmpty()
    }

    @Test
    fun `giving up keeps the limits recorded until the restore is confirmed`() = runTest {
        val flaky = FlakyWrites(writesAllowed = 4)
        val controller = controllerWriting(flaky)
        controller.apply(CpuProfile.BALANCED)
        sysfs.rejectWrites += "policy8"
        repeat(CpuProfileRules.MAX_REAPPLY_FAILURES) {
            sysfs.halReset("policy8")
            controller.enforce()
        }

        // The restore went unanswered: nothing may be forgotten yet.
        assertThat(controller.status.value.holdsCaps).isTrue()
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)

        flaky.writesAllowed = Int.MAX_VALUE
        controller.enforce()

        val status = controller.status.value
        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        assertThat(status.pending).isFalse()
        assertThat(status.held).isEmpty()
        assertThat(status.active).isEqualTo(CpuProfile.STOCK)
        assertThat(status.notice).isEqualTo(CpuProfileNotice.ANDROID_KEEPS_CHANGING)
    }

    @Test
    fun `stock under a limit nobody explains still releases, never caps again`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.hiddenLimit("policy8", 2_000_000L)

        val outcome = controller.apply(CpuProfile.STOCK)

        assertThat(outcome).isEqualTo(ApplyOutcome.HeldLower)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(controller.status.value.held).isEmpty()
        assertThat(controller.restoreStockBeforeRootOff()).isTrue()
    }

    @Test
    fun `a restart's profile whose first read fails is tried again on the next pass`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        controller.apply(CpuProfile.BALANCED)
        reboot()
        sysfs.failCommands = true

        controller.enforce()

        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(store.state.bootProfile).isEqualTo(CpuProfile.BALANCED)

        sysfs.failCommands = false
        controller.enforce()

        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(store.state.bootProfile).isNull()
    }

    @Test
    fun `switching apply-after-restart off drops a profile still waiting`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        controller.apply(CpuProfile.BALANCED)
        reboot()
        controller.setApplyAfterRestart(false)
        val writes = sysfs.writes.size

        controller.enforce()

        assertThat(store.state.bootProfile).isNull()
        assertThat(sysfs.writes).hasSize(writes)
    }

    @Test
    fun `root going off drops a profile still waiting`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        controller.apply(CpuProfile.BALANCED)
        reboot()

        assertThat(controller.restoreStockBeforeRootOff()).isTrue()

        assertThat(store.state.bootProfile).isNull()
    }

    @Test
    fun `a held cluster that is offline at stock stays recorded and is released when it returns`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.policies.getValue("policy8").offline = true

        controller.apply(CpuProfile.STOCK)

        assertThat(sysfs.effective("policy4")).isEqualTo(2_367_000L)
        assertThat(controller.status.value.held).isEmpty()
        assertThat(controller.status.value.releasing).containsExactly(p8, 2_294_000L)
        assertThat(controller.status.value.holdsCaps).isTrue()
        assertThat(controller.restoreStockBeforeRootOff()).isFalse()

        settings.access = PrivilegedAccess.ROOT
        sysfs.policies.getValue("policy8").offline = false
        controller.enforce()

        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(controller.status.value.held).isEmpty()
    }

    @Test
    fun `a preset never raises a lower limit android holds in the same file`() = runTest {
        sysfs.halLimit("policy8", 1_557_000L)
        powerSave = true
        val controller = controller()

        val outcome = controller.apply(CpuProfile.BALANCED)

        assertThat(outcome).isEqualTo(ApplyOutcome.HeldLower)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(1_557_000L)
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)
        assertThat(controller.status.value.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)

        // Battery saver ends: Android lifts its limit, and the pass sets the profile's.
        powerSave = false
        sysfs.halReset("policy8")
        controller.enforce()

        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
    }

    @Test
    fun `a custom apply settled after a lost answer keeps the new custom values`() = runTest {
        val lossy = controllerWriting(object : RootCommandShell {
            override fun run(command: String, timeoutMillis: Long): String? =
                gate.run(command, timeoutMillis).takeUnless { command.startsWith("echo ") }
        })
        lossy.apply(CpuProfile.CUSTOM, mapOf(p8 to 2_049_000L))

        val controller = controller()
        controller.enforce()

        assertThat(controller.status.value.active).isEqualTo(CpuProfile.CUSTOM)
        assertThat(controller.status.value.custom).containsExactly(p8, 2_049_000L)
        assertThat(controller.status.value.held).containsExactly(p8, 2_049_000L)
    }

    @Test
    fun `stock under heat at exactly the held limit still releases`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.throttle("policy8", 2_294_000L)

        val outcome = controller.apply(CpuProfile.STOCK)

        assertThat(outcome).isEqualTo(ApplyOutcome.HeldLower)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(controller.status.value.held).isEmpty()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
    }

    @Test
    fun `root off on a hot phone still restores`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.throttle("policy8", 2_294_000L)

        assertThat(controller.restoreStockBeforeRootOff()).isTrue()
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
    }

    @Test
    fun `a refused limit undoes only the limits, never a release`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.CUSTOM, mapOf(p0 to 1_098_000L, p8 to 1_745_000L))
        sysfs.rejectWrites += "policy8"

        val outcome = controller.apply(CpuProfile.BALANCED)

        assertThat(outcome).isEqualTo(ApplyOutcome.NotSupported(setOf(p8)))
        // The little cluster went back to its own limit and stays there.
        assertThat(sysfs.policies.getValue("policy0").request).isEqualTo(1_704_000L)
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(1_745_000L)
        assertThat(controller.status.value.held).containsExactly(p8, 1_745_000L)
    }

    @Test
    fun `heat named the mainline way is heat too`() = runTest {
        sysfs.mainlineCoolingNames = true
        sysfs.throttle("policy8", 1_557_000L)
        val controller = controller()

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.HeldLower)
        // Read under heat, the original is the maximum, not heat's limit.
        assertThat(store.state.originals[p8]).isEqualTo(2_914_000L)
        assertThat(store.state.originalsAssumed).contains(p8)
    }

    @Test
    fun `a framework restart on the same kernel boot keeps holding`() = runTest {
        controller().apply(CpuProfile.BALANCED)
        // Same boot id: only the framework restarted; the kernel kept the limits.
        val restarted = controller()
        sysfs.halReset("policy8")

        restarted.enforce()

        assertThat(restarted.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
    }

    @Test
    fun `a profile waiting after a restart shows in the status`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        controller.apply(CpuProfile.BALANCED)
        reboot()
        sysfs.failCommands = true

        controller.enforce()

        assertThat(controller.status.value.waitingAfterRestart).isTrue()
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a release that did not show is tried again, never held as a limit`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.CUSTOM, mapOf(p0 to 1_098_000L, p8 to 1_745_000L))
        sysfs.rejectWrites += "policy0"

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)

        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(status.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
        assertThat(status.releasing).containsExactly(p0, 1_098_000L)
        assertThat(status.holdsCaps).isTrue()

        // Still refused: the little cluster reads the old limit, which is no reason to keep it.
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy0").request).isEqualTo(1_098_000L)
        assertThat(controller.status.value.releasing).containsExactly(p0, 1_098_000L)

        sysfs.rejectWrites.clear()
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy0").request).isEqualTo(1_704_000L)
        assertThat(controller.status.value.releasing).isEmpty()
        assertThat(controller.status.value.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `a release android raised meanwhile is never put back as a limit`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.CUSTOM, mapOf(p0 to 1_098_000L))
        sysfs.rejectWrites += "policy0"
        controller.apply(CpuProfile.STOCK)
        sysfs.rejectWrites.clear()
        sysfs.halReset("policy0")
        val writesBefore = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.effective("policy0")).isEqualTo(1_704_000L)
        assertThat(sysfs.writes.drop(writesBefore)).doesNotContain("policy0" to 1_098_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a release root never ran is not taken as done because heat reads low`() = runTest {
        val flaky = FlakyWrites()
        val controller = controllerWriting(flaky)
        controller.apply(CpuProfile.BALANCED)
        // The screen went off between the read and the write: nothing ran.
        flaky.writesAllowed = flaky.writes
        assertThat(controller.apply(CpuProfile.STOCK)).isEqualTo(ApplyOutcome.Unconfirmed)
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(1_836_000L)

        // Heat now holds both clusters at exactly the app's limits.
        sysfs.throttle("policy4", 1_836_000L)
        sysfs.throttle("policy8", 2_294_000L)
        flaky.writesAllowed = Int.MAX_VALUE
        controller.enforce()

        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a stock written again keeps a cluster the kernel did not answer for`() = runTest {
        val flaky = FlakyWrites()
        val controller = controllerWriting(flaky)
        controller.apply(CpuProfile.BALANCED)
        flaky.writesAllowed = flaky.writes
        controller.apply(CpuProfile.STOCK)
        flaky.writesAllowed = Int.MAX_VALUE
        sysfs.policies.getValue("policy8").offline = true

        controller.enforce()

        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.holdsCaps).isTrue()
        assertThat(controller.restoreStockBeforeRootOff()).isFalse()

        sysfs.policies.getValue("policy8").offline = false
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `giving up keeps a cluster whose restore the kernel refused`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.rejectWrites += "policy4"
        sysfs.rejectWrites += "policy8"
        repeat(CpuProfileRules.MAX_REAPPLY_FAILURES) {
            sysfs.halReset("policy4")
            controller.enforce()
        }

        val status = controller.status.value
        assertThat(status.active).isEqualTo(CpuProfile.STOCK)
        assertThat(status.notice).isEqualTo(CpuProfileNotice.ANDROID_KEEPS_CHANGING)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_294_000L)
        assertThat(status.holdsCaps).isTrue()

        sysfs.rejectWrites.clear()
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a rollback the kernel refused keeps the limit on record`() = runTest {
        sysfs.rejectWrites += "policy8"
        val controller = controllerWriting(ScriptedWrites(refuseFrom = mapOf(2 to "policy4")))

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.NotSupported(setOf(p8)))

        // The middle cluster took the limit, and the kernel then refused to let it go.
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(1_836_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.held).isEmpty()
        assertThat(controller.status.value.holdsCaps).isTrue()

        sysfs.rejectWrites.clear()
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a pending apply rolled back on the next pass keeps a limit the kernel refused to let go`() = runTest {
        sysfs.rejectWrites += "policy8"
        // Write 1 is the apply, its answer lost; 2 the pass's second try; 3 the rollback, refused for the middle cluster.
        val controller = controllerWriting(ScriptedWrites(loseAnswers = setOf(1), refuseFrom = mapOf(3 to "policy4")))
        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)

        controller.enforce()

        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(1_836_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.holdsCaps).isTrue()

        sysfs.rejectWrites.clear()
        controller.enforce()
        assertThat(sysfs.policies.getValue("policy4").request).isEqualTo(2_367_000L)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `an apply whose record cannot be saved writes nothing`() = runTest {
        store.failSaves = true
        val controller = controller()

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.NotSaved)

        assertThat(sysfs.writes).isEmpty()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.holdsCaps).isFalse()
    }

    @Test
    fun `a write-ahead that cannot be saved stops the apply before the kernel`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        store.failSaves = true

        assertThat(controller.apply(CpuProfile.POWER_SAVING)).isEqualTo(ApplyOutcome.NotSaved)

        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(controller.status.value.pending).isFalse()
    }

    @Test
    fun `giving up without a record to save lets nothing go yet`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.rejectWrites += "policy4"
        repeat(CpuProfileRules.MAX_REAPPLY_FAILURES - 1) {
            sysfs.halReset("policy4")
            controller.enforce()
        }
        store.failSaves = true
        sysfs.halReset("policy4")
        val writesBefore = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.writes.drop(writesBefore)).isEmpty()
        assertThat(sysfs.effective("policy8")).isEqualTo(2_294_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(controller.status.value.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `a limit held unwritten that cannot be recorded is not reported as set`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        controller.apply(CpuProfile.STOCK)
        // Battery saver holds both clusters under the targets: nothing would be written.
        sysfs.halLimit("policy4", 1_418_000L)
        sysfs.halLimit("policy8", 1_557_000L)
        store.failSaves = true

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.NotSaved)

        assertThat(controller.status.value.active).isEqualTo(CpuProfile.STOCK)
        assertThat(controller.status.value.held).isEmpty()
    }

    @Test
    fun `a restart whose record cannot be saved writes nothing`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        reboot()
        store.failSaves = true
        val writesBefore = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.writes.drop(writesBefore)).isEmpty()
        assertThat(controller.apply(CpuProfile.STOCK)).isEqualTo(ApplyOutcome.NotSaved)
        assertThat(sysfs.writes.drop(writesBefore)).isEmpty()
    }

    @Test
    fun `without root and nothing held, a pass reads nothing at all`() = runTest {
        settings.access = PrivilegedAccess.OFF
        val controller = controller()
        val loadsBefore = store.loads
        val bootReadsBefore = bootReads

        controller.enforce()

        assertThat(store.loads).isEqualTo(loadsBefore)
        assertThat(bootReads).isEqualTo(bootReadsBefore)
        assertThat(sysfs.commands).isEmpty()
    }

    @Test
    fun `a limit android held when the profile was chosen is not the phone's own once android lets go`() = runTest {
        // A power hint holds the big cluster at the moment the profile is first chosen.
        sysfs.halLimit("policy8", 1_557_000L)
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        assertThat(store.state.originals[p8]).isEqualTo(1_557_000L)

        // The hint ends and the cluster shows what the phone itself allows.
        sysfs.halReset("policy8")
        controller.enforce()

        assertThat(store.state.originals[p8]).isEqualTo(2_914_000L)
        assertThat(controller.status.value.held[p8]).isEqualTo(2_294_000L)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_294_000L)
        controller.apply(CpuProfile.STOCK)
        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_914_000L)
    }

    @Test
    fun `a refused limit is refused even while another cluster is hot`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.CUSTOM, mapOf(p8 to 1_745_000L))
        sysfs.throttle("policy4", 1_836_000L)
        sysfs.rejectWrites += "policy8"

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.NotSupported(setOf(p8)))

        assertThat(controller.status.value.active).isEqualTo(CpuProfile.CUSTOM)
        assertThat(controller.status.value.held).containsExactly(p8, 1_745_000L)
    }

    @Test
    fun `a release the kernel refused stays on the list even while another cluster is hot`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        sysfs.throttle("policy4", 1_836_000L)
        sysfs.rejectWrites += "policy8"

        assertThat(controller.restoreStockBeforeRootOff()).isFalse()

        assertThat(controller.status.value.releasing).containsExactly(p8, 2_294_000L)
    }

    @Test
    fun `the profile reads only the clusters and the heat, never the slower sensors`() = runTest {
        controller().apply(CpuProfile.BALANCED)
        assertThat(sysfs.commands.first()).isEqualTo(KernelCommands.CPU_READ)
        assertThat(sysfs.commands).doesNotContain(KernelCommands.FULL_READ)
    }

    @Test
    fun `a limit still in the kernel from a refused release is written over, never taken for android's`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.POWER_SAVING)
        sysfs.rejectWrites += "policy8"
        controller.apply(CpuProfile.STOCK)
        assertThat(controller.status.value.releasing).containsExactly(p8, 1_745_000L)
        sysfs.rejectWrites.clear()

        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Applied)

        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(2_294_000L)
        assertThat(controller.status.value.releasing).isEmpty()
        // Refused again on the way back: the limit that is there now is the one on record.
        sysfs.rejectWrites += "policy8"
        assertThat(controller.restoreStockBeforeRootOff()).isFalse()
        assertThat(controller.status.value.releasing).containsExactly(p8, 2_294_000L)
    }

    @Test
    fun `a record the disk refused is not run on the next pass`() = runTest {
        val controller = controller()
        controller.apply(CpuProfile.BALANCED)
        store.failSaves = true
        assertThat(controller.apply(CpuProfile.POWER_SAVING)).isEqualTo(ApplyOutcome.NotSaved)
        val writesBefore = sysfs.writes.size

        controller.enforce()

        assertThat(sysfs.writes.drop(writesBefore)).isEmpty()
        assertThat(sysfs.effective("policy4")).isEqualTo(1_836_000L)
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(controller.status.value.pending).isFalse()
    }

    @Test
    fun `a pending limit android has since held lower waits, as a fresh apply would`() = runTest {
        val controller = controllerWriting(ScriptedWrites(loseAnswers = setOf(1)))
        assertThat(controller.apply(CpuProfile.BALANCED)).isEqualTo(ApplyOutcome.Unconfirmed)
        // Android puts a lower limit of its own on the big cluster before the next pass, while another cluster is hot.
        sysfs.halLimit("policy8", 1_557_000L)
        sysfs.throttle("policy4", 1_836_000L)

        controller.enforce()

        assertThat(sysfs.policies.getValue("policy8").request).isEqualTo(1_557_000L)
        assertThat(controller.status.value.pending).isFalse()
        assertThat(controller.status.value.active).isEqualTo(CpuProfile.BALANCED)
        assertThat(controller.status.value.held).containsExactly(p4, 1_836_000L, p8, 2_294_000L)
    }

    @Test
    fun `a cluster missing from the read does not pin heat on the wrong one`() = runTest {
        // The little cluster's cores are off, so it is missing from the read; its cooling device is not.
        sysfs.policies.getValue("policy0").offline = true
        sysfs.throttle("policy4", 1_418_000L)
        val controller = controller()

        controller.apply(CpuProfile.BALANCED)

        // Read under heat, the hot cluster's original is its maximum, not heat's limit, and
        // the big cluster's reading at its maximum is taken as read.
        assertThat(store.state.originals).containsExactly(p4, 2_367_000L, p8, 2_914_000L)
        assertThat(store.state.originalsAssumed).containsExactly(p4)
    }

    @Test
    fun `the apply-after-restart switch is saved`() = runTest {
        val controller = controller()
        controller.setApplyAfterRestart(true)
        assertThat(controller.status.value.applyAfterRestart).isTrue()
        assertThat(store.state.applyAfterRestart).isTrue()
    }
}
