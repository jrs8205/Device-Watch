package org.jarsi.devicewatch.presentation

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.BatteryHistory
import org.jarsi.devicewatch.data.BatteryHistoryCodec
import org.jarsi.devicewatch.data.BatterySample
import org.jarsi.devicewatch.data.DeviceInfo
import org.jarsi.devicewatch.data.MonthlyDataUsage
import org.jarsi.devicewatch.data.NotificationLog
import org.jarsi.devicewatch.data.NotificationLogEntry
import org.jarsi.devicewatch.data.NotificationStats
import org.jarsi.devicewatch.data.SystemStatsRepository
import org.jarsi.devicewatch.data.UNAVAILABLE_TEXT
import org.jarsi.devicewatch.data.UsageHistory
import org.jarsi.devicewatch.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class HistoryDay(
    val day: LocalDate,
    val screenTimeMillis: Long,
    val unlocks: Int,
    val notifications: Int,
    val boots: Int,
    val charges: Int,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    /** True only while a pull-to-refresh-initiated load is running. */
    val isRefreshing: Boolean = false,
    val days: List<HistoryDay> = emptyList(),
    val monthlyUsage: List<MonthlyDataUsage> = emptyList(),
    val logEntries: List<NotificationLogEntry> = emptyList(),
    val listenerEnabled: Boolean = false,
    /** The store's whole retained window, ascending; the chart picks its own range out of it. */
    val batterySamples: List<BatterySample> = emptyList(),
)

/**
 * State for the Historia page: 62-day tallies + monthly data usage + the
 * notification log + the retained battery history.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val usageHistory: UsageHistory,
    private val notificationStats: NotificationStats,
    private val notificationLog: NotificationLog,
    private val statsRepository: SystemStatsRepository,
    private val batteryHistory: BatteryHistory,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var pendingMonthlyRefresh = false
    private var pullStartMillis = 0L

    /**
     * Pull-to-refresh entry: also re-fetches the full monthly table. The flag is
     * set BEFORE load()'s overlap guard so a pull colliding with a 15 s poll tick
     * still shows the indicator; a skipped full re-fetch is caught up by the next
     * tick via [pendingMonthlyRefresh].
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        pullStartMillis = System.currentTimeMillis()
        pendingMonthlyRefresh = true
        load()
    }

    fun load(refreshMonthlyUsage: Boolean = false) {
        // Overlapping loads must not race: a stale poll finishing after a pull
        // would write pre-refresh monthly data back over the fresh table. A pull
        // that lands mid-poll is chained to run right after, not dropped.
        if (loadJob?.isActive == true) {
            if (pendingMonthlyRefresh) {
                val activeJob = loadJob
                viewModelScope.launch {
                    activeJob?.join()
                    load()
                }
            }
            return
        }
        val forceMonthly = refreshMonthlyUsage || pendingMonthlyRefresh
        pendingMonthlyRefresh = false
        loadJob = viewModelScope.launch {
            try {
                // Loading is only surfaced on the very first load; later calls are
                // silent refreshes so the open page never flashes its empty states.
                _uiState.update { it.copy(isLoading = it.days.isEmpty()) }
                val previousMonthly = _uiState.value.monthlyUsage
                val state = withContext(dispatcher) {
                    val today = LocalDate.now()
                    val start = today.minusDays(61)
                    val days = usageHistory.dailyTallies(start, today).map { tally ->
                        HistoryDay(
                            day = tally.day,
                            screenTimeMillis = tally.screenTimeMillis,
                            unlocks = tally.unlocks,
                            notifications = notificationStats.totalForDay(tally.day),
                            boots = tally.boots,
                            charges = tally.charges,
                        )
                    }
                    HistoryUiState(
                        isLoading = false,
                        days = days,
                        monthlyUsage = resolveMonthlyUsage(forceMonthly, previousMonthly),
                        logEntries = notificationLog.entriesNewestFirst(),
                        listenerEnabled = notificationStats.isListenerEnabled(),
                        batterySamples = batteryHistory.samplesSince(
                            System.currentTimeMillis() - BATTERY_WINDOW_MILLIS
                        ),
                    )
                }
                // Preserve the pull flag across the whole-state write; the finally
                // block clears it after the indicator's minimum display time.
                _uiState.update { current -> state.copy(isRefreshing = current.isRefreshing) }
            } finally {
                // A pull queued behind this load keeps the indicator up — the
                // chained follow-up load clears it when the pull actually ran.
                if (!pendingMonthlyRefresh) {
                    if (_uiState.value.isRefreshing) delayForPullIndicator(pullStartMillis)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /**
     * Past months are immutable, so a valid cache only re-queries the live current
     * month on each poll. A full re-fetch happens when forced (pull), when nothing
     * is cached, when the cache is entirely unavailable (usage access was granted
     * after it was built), or after a month rollover.
     */
    private suspend fun resolveMonthlyUsage(
        force: Boolean,
        previous: List<MonthlyDataUsage>,
    ): List<MonthlyDataUsage> {
        val cacheUsable = !force &&
            previous.isNotEmpty() &&
            previous.first().month == YearMonth.now() &&
            !previous.all { it.mobileGb < 0.0 && it.wifiGb < 0.0 }
        return if (cacheUsable) {
            statsRepository.monthlyDataUsage(monthsBack = 0) + previous.drop(1)
        } else {
            statsRepository.monthlyDataUsage()
        }
    }

    /** Writes the 62-day tallies as CSV under cacheDir/exports and opens the share sheet. */
    fun exportUsageHistoryCsv(context: Context) {
        exportFile(context, fileStem = "device-watch-usage", extension = "csv", mimeType = "text/csv") {
            CsvExporter.usageHistoryCsv(_uiState.value.days)
        }
    }

    /** Writes the retained notification log as CSV and opens the share sheet. */
    fun exportNotificationLogCsv(context: Context) {
        exportFile(
            context,
            fileStem = "device-watch-notifications",
            extension = "csv",
            mimeType = "text/csv",
        ) {
            CsvExporter.notificationLogCsv(_uiState.value.logEntries, ZoneId.systemDefault())
        }
    }

    /**
     * Writes the whole Historia page as one self-contained HTML file — the form
     * meant for reading, where CSV is the form meant for spreadsheets.
     */
    fun exportHtmlReport(context: Context) {
        val labels = htmlReportLabels(context)
        exportFile(
            context,
            fileStem = "device-watch-report",
            extension = "html",
            mimeType = "text/html",
        ) {
            val state = _uiState.value
            HtmlReportBuilder.build(
                HtmlReportData(
                    deviceName = deviceName(statsRepository.getDeviceInfo()),
                    generatedAt = LocalDateTime.now(),
                    days = state.days,
                    monthly = state.monthlyUsage,
                    batterySamples = state.batterySamples,
                    logEntries = state.logEntries,
                    zone = ZoneId.systemDefault(),
                    locale = Locale.getDefault(),
                ),
                labels,
            )
        }
    }

    private fun exportFile(
        context: Context,
        fileStem: String,
        extension: String,
        mimeType: String,
        content: suspend () -> String,
    ) {
        viewModelScope.launch {
            try {
                val file = withContext(dispatcher) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    purgeStaleExports(dir)
                    val body = content()
                    File(dir, "$fileStem-${LocalDate.now()}.$extension").apply { writeText(body) }
                }
                val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed export must never take the page down; the share sheet
                // simply does not open.
                e.printStackTrace()
            }
        }
    }

    /**
     * Yesterday's exports have already been shared or dropped, and the receiving
     * app holds its own copy — keeping them only grows the cache. Today's stay:
     * a share sheet may still be reading the file the user just sent.
     */
    private fun purgeStaleExports(dir: File) {
        val cutoff = System.currentTimeMillis() - EXPORT_RETENTION_MILLIS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    /** "Google Pixel 8a", but without repeating a manufacturer the model already names. */
    private fun deviceName(info: DeviceInfo): String {
        val model = info.model.takeIf { it != UNAVAILABLE_TEXT && it.isNotBlank() }
        val maker = info.manufacturer.takeIf { it != UNAVAILABLE_TEXT && it.isNotBlank() }
        return when {
            model == null -> maker ?: ""
            maker == null || model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    private fun htmlReportLabels(context: Context) = HtmlReportLabels(
        title = context.getString(R.string.report_title),
        generatedAt = context.getString(R.string.report_generated_at),
        summarySection = context.getString(R.string.report_summary_section),
        summaryScreenTime = context.getString(R.string.report_summary_screen_time),
        summaryUnlocks = context.getString(R.string.report_summary_unlocks),
        summaryNotifications = context.getString(R.string.report_summary_notifications),
        summaryData = context.getString(R.string.report_summary_data),
        batterySection = context.getString(R.string.report_battery_section),
        batteryChargingNote = context.getString(R.string.report_battery_charging_note),
        daysSection = context.getString(R.string.report_days_section),
        columnDay = context.getString(R.string.report_column_day),
        columnScreenTime = context.getString(R.string.report_column_screen_time),
        columnUnlocks = context.getString(R.string.report_column_unlocks),
        columnNotifications = context.getString(R.string.report_column_notifications),
        columnBoots = context.getString(R.string.report_column_boots),
        columnCharges = context.getString(R.string.report_column_charges),
        monthlySection = context.getString(R.string.report_monthly_section),
        columnMonth = context.getString(R.string.report_column_month),
        columnMobile = context.getString(R.string.report_column_mobile),
        columnWifi = context.getString(R.string.report_column_wifi),
        logSection = context.getString(R.string.report_log_section),
        columnTime = context.getString(R.string.report_column_time),
        columnApp = context.getString(R.string.report_column_app),
        columnTitle = context.getString(R.string.report_column_title),
        columnText = context.getString(R.string.report_column_text),
        empty = context.getString(R.string.report_empty),
        footer = context.getString(R.string.report_footer),
        hourUnit = context.getString(R.string.report_hour_unit),
        minuteUnit = context.getString(R.string.report_minute_unit),
        searchDays = context.getString(R.string.report_search_days),
        searchLog = context.getString(R.string.report_search_log),
        searchHintDays = context.getString(R.string.report_search_hint_days),
        searchHintLog = context.getString(R.string.report_search_hint_log),
        rangeWeek = context.getString(R.string.report_range_week),
        rangeMonth = context.getString(R.string.report_range_month),
        rangeAll = context.getString(R.string.report_range_all),
        rangeFrom = context.getString(R.string.report_range_from),
        rangeTo = context.getString(R.string.report_range_to),
        rangeReset = context.getString(R.string.report_range_reset),
        showingCount = context.getString(R.string.report_showing_count),
        noMatches = context.getString(R.string.report_no_matches),
    )

    private companion object {
        /**
         * Wide enough to reach every sample the store still keeps — it retains whole
         * day files, so the oldest one holds between 13 and 14 days of history
         * depending on the time of day. The day/week chart ranges are cut from this.
         */
        const val BATTERY_WINDOW_MILLIS = BatteryHistoryCodec.RETENTION_DAYS * 24 * 60 * 60 * 1000

        /** Matches the manifest's FileProvider authority. */
        const val FILE_PROVIDER_AUTHORITY = "org.jarsi.devicewatch.fileprovider"

        /** How long a written export stays in the cache before the next export clears it. */
        const val EXPORT_RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }
}
