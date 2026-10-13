package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The reads Android counts as location reads live in exactly one gated helper each,
 * so that no new call site can put the app back into its own privacy log. Read from
 * the source, as [KernelRootOnlyGuardTest] reads the constructors: a call that
 * slips in anywhere else fails here, whatever it is for.
 */
class LocationReadGuardTest {

    private val source: String by lazy {
        val path = "src/main/java/org/jarsi/devicewatch/data/SystemStatsRepositoryImpl.kt"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        file.readText()
    }

    /** The name of the function whose body holds [index]. */
    private fun enclosingFunction(index: Int): String {
        val declaration = Regex("""fun\s+([A-Za-z0-9_]+)\s*\(""")
        return declaration.findAll(source.substring(0, index)).lastOrNull()?.groupValues?.get(1) ?: "<none>"
    }

    private fun callers(call: Regex): List<String> = call.findAll(source).map { enclosingFunction(it.range.first) }.toList()

    @Test
    fun `the cell list is read in cellInfo alone`() {
        assertThat(callers(Regex("""\.allCellInfo\b"""))).containsExactly("cellInfo")
    }

    @Test
    fun `the wifi connection info is read in wifiConnectionInfo alone`() {
        assertThat(callers(Regex("""\.connectionInfo\b"""))).containsExactly("wifiConnectionInfo")
    }

    @Test
    fun `the service state is read in readServiceStateNetworkTypeLabel alone`() {
        val callers = callers(Regex("""\.serviceState\b|\.getServiceState\("""))
        assertThat(callers).isNotEmpty()
        assertThat(callers.toSet()).containsExactly("readServiceStateNetworkTypeLabel")
    }

    @Test
    fun `each gated helper asks the gate before it reads`() {
        for (helper in listOf("cellInfo", "wifiConnectionInfo")) {
            val body = source.substringAfter("private fun $helper(").substringBefore("\n    }\n")
            assertThat(body).contains("LocationGatedReads.locationReadAllowed(")
            assertThat(body).contains("appVisibility.visible")
        }
        val serviceState = source.substringAfter("private fun readServiceStateNetworkTypeLabel(").substringBefore("\n    }\n")
        assertThat(serviceState).contains("LocationGatedReads.serviceStateAllowed(")
        assertThat(serviceState).contains("INCLUDE_LOCATION_DATA_NONE")
    }
}
