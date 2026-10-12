package org.jarsi.devicewatch.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A root shell over a pretend cpufreq tree, for the kernel tests. It understands
 * only the grammar [KernelCommands] emits (`echo N > 'path'` and
 * `grep -s -H '^' path…` with `*`/`?` globs) and fails the test on anything else.
 *
 * Each cluster's limit works the way the kernel's frequency QoS does: a write to
 * `scaling_max_freq` replaces the one userspace request (Android's power HAL
 * writes the same file), heat adds a request of its own, and a read returns the
 * lowest of them, never above the hardware maximum.
 */
internal class FakeSysfsRoot(vararg policies: FakePolicy) : RootShell {

    class FakePolicy(
        val name: String,
        val cpus: List<Int>,
        val cpuinfoMin: Long,
        val cpuinfoMax: Long,
        val steps: List<Long>,
    ) {
        /** The userspace request; the power HAL's "no limit" until something writes. */
        var request: Long = HAL_NO_LIMIT
        var thermalCap: Long = Long.MAX_VALUE
        val effective: Long get() = minOf(request, thermalCap, cpuinfoMax)
    }

    val policies: Map<String, FakePolicy> = policies.associateBy { it.name }

    val commands = mutableListOf<String>()

    /** Accepted writes, as (policy name, value). */
    val writes = mutableListOf<Pair<String, Long>>()

    /** Policies whose writes the kernel refuses (EINVAL): the value stays what it was. */
    val rejectWrites = mutableSetOf<String>()

    /** The shell answers nothing: a timeout or a dead `su`. Nothing in the command runs. */
    var failCommands = false

    /** The command runs, but its answer never arrives: the process died mid-apply. */
    var loseAnswers = false

    override var generation = 0
    override val lost = MutableStateFlow(false)

    fun effective(name: String): Long = policies.getValue(name).effective

    /** Android's power HAL resets the request, as after a camera hint. */
    fun halReset(name: String) {
        policies.getValue(name).request = HAL_NO_LIMIT
    }

    /** The power HAL writes a limit of its own, as battery saver or the camera do. */
    fun halLimit(name: String, kHz: Long) {
        policies.getValue(name).request = kHz
    }

    fun throttle(name: String, kHz: Long) {
        policies.getValue(name).thermalCap = kHz
    }

    fun coolDown(name: String) {
        policies.getValue(name).thermalCap = Long.MAX_VALUE
    }

    fun loseRoot() {
        lost.value = true
        failCommands = true
    }

    override fun run(command: String, timeoutMillis: Long): String? = run(command, timeoutMillis, generation)

    override fun run(command: String, timeoutMillis: Long, wanted: Int): String? {
        if (wanted != generation) return null
        commands += command
        if (failCommands) return null
        val output = ArrayList<String>()
        for (statement in command.split("; ")) {
            when {
                statement.startsWith("echo ") -> write(statement)
                statement.startsWith(GREP) -> output += grep(statement.removePrefix(GREP))
                else -> error("unexpected command: $statement")
            }
        }
        return if (loseAnswers) null else output.joinToString("\n")
    }

    override suspend fun requestAccess() = RootAccess.GRANTED

    override fun close() {
        generation++
    }

    private fun write(statement: String) {
        val m = ECHO.matchEntire(statement) ?: error("unexpected write: $statement")
        val name = MAX_PATH.matchEntire(m.groupValues[2])?.groupValues?.get(1) ?: error("write outside the limits: $statement")
        if (name in rejectWrites) return
        val policy = policies[name] ?: return
        val value = m.groupValues[1].toLong()
        policy.request = value
        writes += name to value
    }

    private fun files(): Map<String, List<String>> {
        val files = LinkedHashMap<String, List<String>>()
        policies.values.forEachIndexed { index, p ->
            val dir = "${CpuPolicyId.CPUFREQ_DIR}/${p.name}"
            files["$dir/related_cpus"] = listOf(p.cpus.joinToString(" "))
            files["$dir/cpuinfo_min_freq"] = listOf(p.cpuinfoMin.toString())
            files["$dir/cpuinfo_max_freq"] = listOf(p.cpuinfoMax.toString())
            files["$dir/scaling_min_freq"] = listOf(p.cpuinfoMin.toString())
            files["$dir/scaling_max_freq"] = listOf(p.effective.toString())
            files["$dir/scaling_cur_freq"] = listOf(p.cpuinfoMin.toString())
            files["$dir/scaling_governor"] = listOf("schedutil")
            if (p.steps.isNotEmpty()) files["$dir/scaling_available_frequencies"] = listOf(p.steps.joinToString(" ") + " ")
            val cooling = "/sys/class/thermal/cooling_device$index"
            files["$cooling/type"] = listOf("thermal-cpufreq-$index")
            files["$cooling/cur_state"] = listOf(if (p.thermalCap < p.cpuinfoMax) "1" else "0")
            files["$cooling/max_state"] = listOf("10")
        }
        return files
    }

    private fun grep(args: String): List<String> {
        val files = files()
        return args.split(' ').filter { it.isNotEmpty() }.flatMap { arg ->
            val glob = globRegex(arg.removeSurrounding("'"))
            files.keys.filter(glob::matches).sorted().flatMap { path -> files.getValue(path).map { "$path:$it" } }
        }
    }

    private fun globRegex(pattern: String): Regex = Regex(
        buildString {
            for (c in pattern) when (c) {
                '*' -> append("[^/]*")
                '?' -> append("[^/]")
                else -> append(Regex.escape(c.toString()))
            }
        }
    )

    companion object {
        /** What Pixel's power HAL writes for "no limit"; the kernel holds it to the hardware maximum. */
        const val HAL_NO_LIMIT = 9_999_999L
        private const val GREP = "grep -s -H '^' "
        private val ECHO = Regex("echo (\\d+) > '([^']+)'")
        private val MAX_PATH = Regex(Regex.escape(CpuPolicyId.CPUFREQ_DIR) + "/(policy\\d+)/scaling_max_freq")

        /** The Pixel 8a's three clusters, with their real steps. */
        fun pixel8a() = FakeSysfsRoot(
            FakePolicy(
                "policy0", listOf(0, 1, 2, 3), 324_000, 1_704_000,
                listOf(324_000, 610_000, 820_000, 955_000, 1_098_000, 1_197_000, 1_328_000, 1_425_000, 1_548_000, 1_704_000),
            ),
            FakePolicy(
                "policy4", listOf(4, 5, 6, 7), 402_000, 2_367_000,
                listOf(
                    402_000, 578_000, 697_000, 712_000, 910_000, 1_065_000, 1_221_000, 1_328_000,
                    1_418_000, 1_572_000, 1_836_000, 1_945_000, 2_130_000, 2_245_000, 2_367_000,
                ),
            ),
            FakePolicy(
                "policy8", listOf(8), 500_000, 2_914_000,
                listOf(
                    500_000, 880_000, 1_164_000, 1_298_000, 1_557_000, 1_745_000, 1_885_000, 2_049_000,
                    2_147_000, 2_294_000, 2_363_000, 2_556_000, 2_687_000, 2_850_000, 2_914_000,
                ),
            ),
        )
    }
}
