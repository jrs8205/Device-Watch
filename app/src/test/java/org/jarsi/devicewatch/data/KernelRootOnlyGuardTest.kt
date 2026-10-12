package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The kernel code can reach a shell only through [RootCommandShell]. Were any of
 * these classes handed the shell that routes to Shizuku, a kernel write could run
 * as the ADB user; checked on the constructors, where Hilt would wire it in.
 */
class KernelRootOnlyGuardTest {

    private val forbidden = listOf(PrivilegedShell::class.java, ShizukuShell::class.java, SelectedPrivilegedShell::class.java)

    @Test
    fun `kernel classes take no shell that could route to shizuku`() {
        for (type in listOf(CpuLimitWriter::class.java, ShellKernelSnapshotSource::class.java, CpuProfileController::class.java)) {
            val parameters = type.declaredConstructors.flatMap { it.parameterTypes.toList() }
            for (parameter in parameters) {
                for (bad in forbidden) {
                    assertWithMessage("${type.simpleName} takes ${parameter.simpleName}")
                        .that(bad.isAssignableFrom(parameter) || parameter.isAssignableFrom(bad) && parameter != Any::class.java)
                        .isFalse()
                }
            }
        }
    }
}
