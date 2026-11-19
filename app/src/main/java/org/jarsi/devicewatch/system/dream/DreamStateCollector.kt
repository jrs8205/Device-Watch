package org.jarsi.devicewatch.system.dream

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jarsi.devicewatch.LatinDigits
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.data.DeviceLocalFlags
import org.jarsi.devicewatch.data.batteryPowerWatts
import org.jarsi.devicewatch.system.ActiveNotificationsStore
import org.jarsi.devicewatch.system.BatteryFullNotifier
import org.jarsi.devicewatch.system.chargeCompletionText
import org.jarsi.devicewatch.system.newArrivalKeys
import org.jarsi.devicewatch.system.screensaverDeviceName
import org.jarsi.devicewatch.system.visibleNotificationGroups
import org.jarsi.devicewatch.system.visibleNotificationKeys
import java.time.ZoneId
import java.util.Date

/**
 * Collects everything the running screensaver shows into one [DreamUiState]: the battery
 * broadcast, a clock that ticks on the second, the next alarm, the unread notifications
 * and, when the Gauges style's calendar rows are on, the next calendar events.
 * [onMinute] runs when the minute changes, and once at the start, for the once-a-minute
 * work that belongs to the window rather than to the state (the dim check).
 */
@Composable
internal fun rememberDreamUiState(
    context: Context,
    options: DreamOptions,
    onMinute: (minuteOfDay: Int) -> Unit,
): DreamUiState {
    var batteryLevel by remember { mutableIntStateOf(50) }
    var chargeState by remember { mutableStateOf(DreamChargeState.ON_BATTERY) }
    var batteryTemp by remember { mutableDoubleStateOf(25.6) }
    var batteryVoltage by remember { mutableDoubleStateOf(3.88) }
    var chargeFullAtMs by remember { mutableLongStateOf(-1L) }
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var chargeWatts by remember { mutableStateOf<Double?>(null) }
    var hasSentFullBatteryNotification by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100) / scale
                }

                // One state drives the icon, the word, the pulse, the wattage and the estimate, so
                // they cannot disagree: only a battery that is still filling counts as charging.
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val state = dreamChargeState(status, plugged, batteryLevel)
                chargeState = state
                val charging = state == DreamChargeState.CHARGING

                if (state == DreamChargeState.FULL) {
                    if (!hasSentFullBatteryNotification) {
                        BatteryFullNotifier.show(context.applicationContext)
                        hasSentFullBatteryNotification = true
                    }
                } else {
                    hasSentFullBatteryNotification = false
                }

                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTemp = temp / 10.0

                val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                batteryVoltage = volt / 1000.0

                val batteryManager =
                    context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

                // Live charging power. CURRENT_NOW is documented as µA but some OEMs report mA
                // and the sign convention varies; batteryPowerWatts() normalizes both and returns
                // null for missing/implausible readings (then no wattage is shown).
                chargeWatts = if (charging && batteryManager != null) {
                    val currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    batteryPowerWatts(
                        currentRaw,
                        volt / 1000.0,
                        microamps = DeviceLocalFlags.batteryCurrentMicroamps(context, currentRaw),
                    )
                } else {
                    null
                }

                // Capture the absolute "battery full" wall-clock time ONCE, at the moment we
                // read the remaining duration. Previously the raw remaining-duration was stored
                // and added to System.currentTimeMillis() on every recomposition (once a second),
                // which made the estimate drift ~1 s later per second between battery broadcasts.
                chargeFullAtMs = if (charging && batteryManager != null &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val remaining = batteryManager.computeChargeTimeRemaining()
                    if (remaining > 0L) System.currentTimeMillis() + remaining else -1L
                } else {
                    -1L
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var clockTexts by remember { mutableStateOf(DreamClockTexts("", "", "", false, "", "", "")) }
    var alarmText by remember { mutableStateOf("") }

    // Unread notifications under the clock, from the listener's snapshot. The user setting
    // is read once per dream: it is changed in the app, never while the dream is up.
    val showNotificationIcons = options.showNotifications
    // Read once per dream, like the other settings: a rename in Android's About phone
    // shows at the next start.
    val deviceName = remember(context, options.showDeviceName) {
        if (options.showDeviceName) {
            screensaverDeviceName(
                deviceName = android.provider.Settings.Global.getString(
                    context.contentResolver,
                    android.provider.Settings.Global.DEVICE_NAME,
                ),
                model = android.os.Build.MODEL,
                manufacturer = android.os.Build.MANUFACTURER,
            )
        } else {
            null
        }
    }
    val activeNotifications by ActiveNotificationsStore.notifications.collectAsState()
    val notificationGroups = remember(activeNotifications, showNotificationIcons) {
        if (showNotificationIcons) visibleNotificationGroups(activeNotifications) else emptyList()
    }
    // Notifications already waiting when the dream starts show quietly; the row pulses
    // only for the ones that arrive while it is up.
    var seenNotificationKeys by remember { mutableStateOf(visibleNotificationKeys(activeNotifications)) }
    var notificationPulseToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeNotifications) {
        val current = visibleNotificationKeys(activeNotifications)
        if (showNotificationIcons && newArrivalKeys(seenNotificationKeys, current).isNotEmpty()) {
            notificationPulseToken++
        }
        seenNotificationKeys = current
    }

    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) { dreamLocale(configuration) }
    // Honor the device's 12/24-hour clock preference instead of hardcoding 24-hour time,
    // so the screensaver clock reads naturally on every locale/brand.
    val is24Hour = remember(configuration) {
        android.text.format.DateFormat.is24HourFormat(context)
    }
    val formats = remember(currentLocale, is24Hour) { dreamClockFormats(context, currentLocale, is24Hour) }
    val currentOnMinute by rememberUpdatedState(onMinute)

    LaunchedEffect(formats) {
        var lastMinuteOfDay = -1

        while (true) {
            val now = Date()
            clockMillis = now.time
            clockTexts = formats.texts(now)

            // Once-a-minute work: the next-alarm line and whatever the window does by the
            // minute (the live dim state, so a screensaver that runs across the night-dim
            // window edges dims/brightens without a restart).
            val minuteOfDay = currentMinuteOfDay()
            if (minuteOfDay != lastMinuteOfDay) {
                lastMinuteOfDay = minuteOfDay
                alarmText = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                    ?.nextAlarmClock?.triggerTime
                    ?.let { formats.alarm(Date(it)) }
                    ?: ""
                currentOnMinute(minuteOfDay)
            }

            // Sleep exactly to the next second boundary so the seconds never visibly skip.
            kotlinx.coroutines.delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    // The calendar, for the Gauges style's rows: read only when those rows are switched
    // on, when the screensaver starts and every ten minutes after, off the main thread.
    // The events live in this state alone and go when the screensaver does.
    var calendarEvents by remember { mutableStateOf<List<DreamCalendarEvent>>(emptyList()) }
    if (options.shows(DreamField.CALENDAR)) {
        LaunchedEffect(context) {
            while (true) {
                calendarEvents = withContext(Dispatchers.IO) {
                    readDreamCalendarEvents(context, System.currentTimeMillis(), ZoneId.systemDefault())
                }
                kotlinx.coroutines.delay(CALENDAR_REFRESH_MS)
            }
        }
    }
    // Worded again each minute from the last read: "today" and "now" move with the clock.
    val calendarMinute = clockMillis / 60_000L
    val calendarRows = remember(calendarEvents, calendarMinute, currentLocale, is24Hour, options.showCalendarTitles) {
        if (calendarEvents.isEmpty()) {
            emptyList()
        } else {
            calendarRows(
                events = calendarEvents,
                nowMs = calendarMinute * 60_000L,
                zone = ZoneId.systemDefault(),
                locale = currentLocale,
                is24Hour = is24Hour,
                showTitles = options.showCalendarTitles,
                nowWord = context.getString(R.string.dream_calendar_now),
                placeholder = context.getString(R.string.dream_calendar_event),
            )
        }
    }

    val charging = chargeState == DreamChargeState.CHARGING
    return DreamUiState(
        timeText = clockTexts.time,
        secondsText = clockTexts.seconds,
        amPmText = clockTexts.amPm,
        amPmLeads = clockTexts.amPmLeads,
        dateText = clockTexts.date,
        shortDateText = clockTexts.shortDate,
        compactDateText = clockTexts.compactDate,
        alarmText = alarmText,
        deviceName = deviceName,
        batteryLevel = batteryLevel,
        chargeState = chargeState,
        voltsText = batteryVoltsText(batteryVoltage, currentLocale),
        tempText = batteryTempText(batteryTemp, currentLocale),
        wattsText = batteryWattsText(chargeWatts, chargeState, currentLocale),
        fullTimeText = when {
            !charging -> ""
            chargeFullAtMs > 0L -> chargeCompletionText(context, chargeFullAtMs, clockMillis, currentLocale, is24Hour)
            else -> context.getString(R.string.dream_full_time_unknown)
        },
        fullClockText = if (charging && chargeFullAtMs > 0L) formats.clock(Date(chargeFullAtMs)) else "",
        notificationGroups = notificationGroups,
        notificationPulseToken = notificationPulseToken,
        clockMillis = clockMillis,
        calendarRows = calendarRows,
    )
}

/**
 * The locale the screensaver formats its clock and figures with. The service is not made
 * anew when the phone is turned: after a turn the screen is handed the phone's
 * configuration as it is, without the numbering system the service's own resources name, so
 * the rule for the digits is applied here again (see [LatinDigits]).
 */
internal fun dreamLocale(configuration: Configuration): Locale = LatinDigits.locale(configuration.locales[0])
