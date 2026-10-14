package org.jarsi.devicewatch.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/** The system image's apps, and their removal for this user and return, through extended access. */
interface SystemAppsRepository {
    /** Every package on the system image, installed for this user or not, with its verdict. */
    suspend fun systemApps(): List<SystemApp>

    /**
     * The verdict for [packageName] as the phone stands now, with its roles read again;
     * null when the package is not installed for this user. For the moment before a
     * removal: the list's verdict can be stale while the page stays on screen.
     */
    suspend fun verdictNow(packageName: String): SystemAppVerdict?

    /** Removes [packageName] for this user. [SystemAppActionResult.NoShell] when extended access is off. */
    suspend fun remove(packageName: String): SystemAppActionResult

    /** Brings a removed [packageName] back for this user. */
    suspend fun restore(packageName: String): SystemAppActionResult
}

/**
 * The list comes from the package manager, which knows a system app removed for
 * the user as one not installed for them; only the two actions go through the
 * shell, Shizuku's or root's, as `pm` commands for this user alone.
 */
@Singleton
class SystemAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: PrivilegedShell,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SystemAppsRepository {

    private val packageManager get() = context.packageManager

    /** Android's user ids are the uid's hundred-thousands: user 0's apps are uids under 100 000. */
    private val userId: Int get() = Process.myUid() / UID_PER_USER

    @Suppress("DEPRECATION") // the flags overload: the ApplicationInfoFlags one is API 33
    override suspend fun systemApps(): List<SystemApp> = withContext(dispatcher) {
        val roles = deviceRoles()
        val applications = try {
            packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (_: Exception) {
            emptyList()
        }
        // A shell that fails to answer once is not asked again this read: each further
        // ask would wait out its own timeout.
        var shellAnswers = true
        applications
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { info ->
                SystemApp(
                    packageName = info.packageName,
                    label = labelOf(info),
                    installed = info.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                    updated = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                    verdict = SystemAppRules.verdict(info.packageName, roles),
                )
            }
            // A package kept out of this user by design (a private space's own app, say)
            // was never removed and has nothing to restore: the package manager's dump
            // tells it from one a user removed. Without the shell's answer it stays listed.
            .filterNot { app ->
                !app.installed && shellAnswers && run {
                    val dump = shell.run(SystemAppCommands.dumpPackage(app.packageName, userId))
                    if (dump == null) shellAnswers = false
                    SystemAppCommands.neverInstalledForUser(dump, userId)
                }
            }
    }

    @Suppress("DEPRECATION") // the flags overload: the ApplicationInfoFlags one is API 33
    override suspend fun verdictNow(packageName: String): SystemAppVerdict? = withContext(dispatcher) {
        // Without MATCH_UNINSTALLED_PACKAGES the package manager does not know a package removed for this user.
        val info = quietly { packageManager.getApplicationInfo(packageName, 0) } ?: return@withContext null
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return@withContext null
        SystemAppRules.verdict(packageName, deviceRoles())
    }

    override suspend fun remove(packageName: String): SystemAppActionResult = withContext(dispatcher) {
        SystemAppCommands.outcome(shell.run(SystemAppCommands.uninstall(packageName, userId), ACTION_TIMEOUT_MILLIS))
    }

    override suspend fun restore(packageName: String): SystemAppActionResult = withContext(dispatcher) {
        SystemAppCommands.outcome(shell.run(SystemAppCommands.restore(packageName, userId), ACTION_TIMEOUT_MILLIS))
    }

    private fun labelOf(info: ApplicationInfo): String = try {
        info.loadLabel(packageManager).toString().ifBlank { info.packageName }
    } catch (_: Exception) {
        info.packageName
    }

    /** Each read on its own: one that fails leaves the others, and the fixed lists, to catch what they can. */
    private fun deviceRoles(): DeviceRoles {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = quietly {
            packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                // No home chosen: the resolver, Android's own, answers in its place.
                ?.takeIf { it != ANDROID_PACKAGE }
        }
        val launchers = quietly {
            packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
                // Settings' hidden FallbackHome (priority -1000) is not a home screen.
                .filter { it.priority >= 0 }
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.orEmpty()
        val currentKeyboard = quietly {
            SystemAppRules.imePackage(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        }
        val enabledKeyboards = quietly {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.orEmpty()
        val defaultDialer = quietly { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
        val defaultSms = quietly { Telephony.Sms.getDefaultSmsPackage(context) }
        val webViewProvider = quietly { WebView.getCurrentWebViewPackage()?.packageName }
        return DeviceRoles(
            ownPackage = context.packageName,
            defaultHome = defaultHome,
            launchers = launchers,
            currentKeyboard = currentKeyboard,
            enabledKeyboards = enabledKeyboards,
            defaultDialer = defaultDialer,
            defaultSms = defaultSms,
            webViewProvider = webViewProvider,
        )
    }

    private inline fun <T> quietly(read: () -> T?): T? = try {
        read()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val UID_PER_USER = 100_000
        const val ANDROID_PACKAGE = "android"

        /** A removal has the package manager stop the app and clear its data: seconds, on a slow phone. */
        const val ACTION_TIMEOUT_MILLIS = 30_000L
    }
}
