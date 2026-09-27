package org.jarsi.devicewatch.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryImplTest {

    private val settings = AppSettingsRepositoryImpl(ApplicationProvider.getApplicationContext())
    private val period = 20_000L

    @Test
    fun `a latch lands on the period and quota it was written for`() {
        settings.setDataQuotaGb(10.0)
        settings.setDataQuotaNotified(period, quotaGb = 10.0, threshold = DataQuotaLogic.WARNING_PERCENT)

        assertThat(settings.dataQuotaNotified(period, 10.0, DataQuotaLogic.WARNING_PERCENT)).isTrue()
        assertThat(settings.dataQuotaNotified(period, 10.0, DataQuotaLogic.REACHED_PERCENT)).isFalse()
        assertThat(settings.dataQuotaNotified(period + 1, 10.0, DataQuotaLogic.WARNING_PERCENT)).isFalse()
    }

    @Test
    fun `a latch written under the old quota after a change cannot silence the new quota`() {
        // The service validated its reading against 10 GB, then the user raised the
        // quota to 100 GB (clearing the latches), and only then did the service
        // write its latch: the new quota's alerts must stay armed.
        settings.setDataQuotaGb(10.0)
        settings.setDataQuotaGb(100.0)
        settings.setDataQuotaNotified(period, quotaGb = 10.0, threshold = DataQuotaLogic.WARNING_PERCENT)
        settings.setDataQuotaNotified(period, quotaGb = 10.0, threshold = DataQuotaLogic.REACHED_PERCENT)

        assertThat(settings.dataQuotaNotified(period, 100.0, DataQuotaLogic.WARNING_PERCENT)).isFalse()
        assertThat(settings.dataQuotaNotified(period, 100.0, DataQuotaLogic.REACHED_PERCENT)).isFalse()
    }

    @Test
    fun `changing the quota re-arms both alerts`() {
        settings.setDataQuotaGb(10.0)
        settings.setDataQuotaNotified(period, 10.0, DataQuotaLogic.WARNING_PERCENT)
        settings.setDataQuotaGb(20.0)
        settings.setDataQuotaGb(10.0)

        assertThat(settings.dataQuotaNotified(period, 10.0, DataQuotaLogic.WARNING_PERCENT)).isFalse()
    }
}
