package org.jarsi.devicewatch.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.jarsi.devicewatch.system.AlertNotifications
import org.jarsi.devicewatch.system.HealthAlertController
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Exercise a new phone's prefs separately from a restart on the original phone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 31])
class AlertStateBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val period = 20_000L
    private val quota = 10.0
    private val backedUp = context.getSharedPreferences(AppSettingsRepositoryImpl.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `posting alerts writes no delivery latches to the backed-up settings`() {
        enableAndLatch(AppSettingsRepositoryImpl(context))

        assertThat(backedUp.all.keys.filter(::isDeliveryLatch)).isEmpty()
    }

    @Test
    fun `recreating the repository on the same phone preserves delivered alerts`() {
        enableAndLatch(AppSettingsRepositoryImpl(context))

        val restarted = AppSettingsRepositoryImpl(context)

        HealthAlert.entries.forEach { assertThat(restarted.alertLatched(it)).isTrue() }
        assertThat(pendingQuota(restarted)).isEmpty()
        assertThat(postHotBattery(restarted)).isEqualTo(0)
    }

    @Test
    fun `legacy backup restores the choices but not delivered alerts`() {
        restoreAndCheck("res/xml/backup_rules.xml", "full-backup-content")
    }

    @Test
    fun `cloud backup restores the choices but not delivered alerts`() {
        restoreAndCheck("res/xml/data_extraction_rules.xml", "cloud-backup")
    }

    @Test
    fun `device transfer restores the choices but not delivered alerts`() {
        restoreAndCheck("res/xml/data_extraction_rules.xml", "device-transfer")
    }

    @Test
    fun `a backup from before the fix cannot silence this phone's first alerts`() {
        // Old backups can contain these keys. Copying them to the local file
        // would still mistake a notification on another phone for one here.
        backedUp.edit().apply {
            HealthAlert.entries.forEach {
                putBoolean("alert_enabled:${it.name.lowercase()}", true)
                putBoolean("alert_latched:${it.name.lowercase()}", true)
            }
            putFloat(AppSettingsRepositoryImpl.KEY_DATA_QUOTA_GB, quota.toFloat())
            putBoolean("data_quota_notified:$period:$quota:80", true)
            putBoolean("data_quota_notified:$period:$quota:100", true)
        }.commit()

        val restored = AppSettingsRepositoryImpl(context)

        assertArmed(restored)
        assertThat(backedUp.all.keys.filter(::isDeliveryLatch)).isEmpty()
        assertThat(postHotBattery(restored)).isEqualTo(1)
        assertThat(postHotBattery(AppSettingsRepositoryImpl(context))).isEqualTo(0)
    }

    @Test
    fun `turning alerts off and changing quota still clear local delivery latches`() {
        val settings = AppSettingsRepositoryImpl(context)
        enableAndLatch(settings)

        HealthAlert.entries.forEach {
            settings.setAlertEnabled(it, false)
            settings.setAlertEnabled(it, true)
        }
        settings.setDataQuotaGb(100.0)
        settings.setDataQuotaGb(quota)

        assertArmed(AppSettingsRepositoryImpl(context))
    }

    @Test
    fun `a switch during posting still rejects a stale local latch`() {
        val settings = AppSettingsRepositoryImpl(context)
        settings.setAlertEnabled(HealthAlert.HOT_BATTERY, true)
        val generation = settings.alertGeneration(HealthAlert.HOT_BATTERY)
        settings.setAlertEnabled(HealthAlert.HOT_BATTERY, false)
        settings.setAlertEnabled(HealthAlert.HOT_BATTERY, true)

        assertThat(settings.latchAlert(HealthAlert.HOT_BATTERY, generation)).isFalse()
        assertThat(settings.alertLatched(HealthAlert.HOT_BATTERY)).isFalse()
    }

    private fun enableAndLatch(settings: AppSettingsRepository) {
        settings.setDataCounterMode(DataCounterMode.BILLING_CYCLE)
        settings.setCycleStartDay(15)
        settings.setDataQuotaGb(quota)
        settings.setChargeLimitPercent(80)
        settings.setClassicLook(true)
        HealthAlert.entries.forEach {
            settings.setAlertEnabled(it, true)
            assertThat(settings.latchAlert(it, settings.alertGeneration(it))).isTrue()
        }
        listOf(80, 100).forEach { settings.setDataQuotaNotified(period, quota, it) }
    }

    private fun restoreAndCheck(path: String, section: String) {
        enableAndLatch(AppSettingsRepositoryImpl(context))
        val source = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
        val rules = if (root.tagName == section) root else root.getElementsByTagName(section).item(0) as Element
        val includes = rules.getElementsByTagName("include")
        // A separate preference namespace models a new device. Only files
        // selected by the production XML are copied, never the original context.
        val newPhone = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("restored_$name", mode)
        }
        for (index in 0 until includes.length) {
            val entry = includes.item(index) as Element
            assertThat(entry.getAttribute("domain")).isEqualTo("sharedpref")
            val name = entry.getAttribute("path").removeSuffix(".xml")
            val snapshot = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            newPhone.getSharedPreferences(name, Context.MODE_PRIVATE).edit().apply {
                snapshot.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        else -> error("Unexpected preference type for $key")
                    }
                }
            }.commit()
        }

        val restored = AppSettingsRepositoryImpl(newPhone)
        assertThat(restored.dataCounterMode()).isEqualTo(DataCounterMode.BILLING_CYCLE)
        assertThat(restored.cycleStartDay()).isEqualTo(15)
        assertThat(restored.chargeLimitPercent()).isEqualTo(80)
        assertThat(restored.classicLook()).isTrue()
        assertArmed(restored)
        assertThat(postHotBattery(restored)).isEqualTo(1)
        // Posting on the new phone now suppresses repeats there, including
        // after recreating the repository; the original phone stays latched.
        assertThat(postHotBattery(AppSettingsRepositoryImpl(newPhone))).isEqualTo(0)
        assertThat(postHotBattery(AppSettingsRepositoryImpl(context))).isEqualTo(0)
        listOf(80, 100).forEach { restored.setDataQuotaNotified(period, quota, it) }
        assertThat(pendingQuota(AppSettingsRepositoryImpl(newPhone))).isEmpty()
    }

    private fun assertArmed(settings: AppSettingsRepository) {
        HealthAlert.entries.forEach {
            assertThat(settings.alertEnabled(it)).isTrue()
            assertThat(settings.alertLatched(it)).isFalse()
        }
        assertThat(settings.dataQuotaGb()).isEqualTo(quota)
        assertThat(pendingQuota(settings)).containsExactly(80, 100).inOrder()
    }

    private fun pendingQuota(settings: AppSettingsRepository) = DataQuotaLogic.pendingThresholds(
        quotaGb = settings.dataQuotaGb(), usedGb = quota,
        notified80 = settings.dataQuotaNotified(period, quota, 80),
        notified100 = settings.dataQuotaNotified(period, quota, 100),
    )

    private fun postHotBattery(settings: AppSettingsRepository): Int {
        var posts = 0
        val controller = HealthAlertController(settings, object : AlertNotifications {
            override fun cancel(alert: HealthAlert) = Unit
        })
        repeat(2) {
            controller.apply(HealthAlert.HOT_BATTERY, { AlertLogic.hotBattery(450, it) }) {
                posts++
                true
            }
        }
        return posts
    }

    private fun isDeliveryLatch(key: String) =
        key.startsWith("alert_latched:") || key.startsWith("data_quota_notified:")
}
