# Device Watch

[![Latest release](https://img.shields.io/github/v/release/jrs8205/Device-Watch?sort=semver)](https://github.com/jrs8205/Device-Watch/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/jrs8205/Device-Watch/total)](https://github.com/jrs8205/Device-Watch/releases)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)

Device Watch is an Android device monitoring app with a Jetpack Glance home screen widget and an interactive screensaver for charging or docked use.

The default app language is English. Finnish users get a localized app name and UI through Android's `values-fi` resources.

## Features

- Home screen widget for battery, memory, CPU, storage, Wi-Fi, mobile network, data usage, uptime, and last update time
- Tapping the widget anywhere opens the app
- Data counters per calendar day or per one-month billing cycle with a configurable start day (month lengths handled automatically); the selection applies to the widget and the in-app data rows
- Tabbed dashboard UI: Overview (live battery ring, RAM/CPU, data and usage counters), Apps (usage insights), Device (hardware, SIM and Wi-Fi details), and Settings
- Apps tab: Digital-Wellbeing-style screen-time donut (top apps + others) with tappable legend, top data consumers today, and a last-opened list (oldest and never-used apps first, reversible) with per-app uninstall
- Per-app detail sheet: screen time, times opened, last opened, data used and notifications today
- Daily counters on the Overview tab: screen unlocks (API 28+) and a filtered notification count — ongoing notifications, group summaries and updates to an existing notification are not counted, so the number stays believable
- Privacy dashboard shortcut for per-app location/microphone/camera usage (system view; that data is not exposed to third-party apps)
- Interactive Android screensaver with a large clock, date, next alarm, charging status, battery percentage, voltage, temperature, and live charging power in watts
- Screensaver clock follows the device 12/24-hour setting, with a second-aligned tick
- Battery-level-tinted background gradient and a softly pulsing charge indicator in the screensaver
- Optional screensaver dimming: manual, or automatic on a configurable night schedule (default 22:00–07:00)
- Remembered screensaver rotation setting for repeated charging sessions; the background gradient mirrors with the 180° layout swap
- Larger screensaver rotation touch target for easier use
- Battery full notification while the screensaver is active
- English default resources with Finnish localization
- Runtime permission handling for location, phone state, nearby Wi-Fi devices, and notifications
- Usage Access shortcut for daily network usage statistics
- Release build configured with R8 minification and resource shrinking

Every metric is real data read from Android and kernel sources. When a value is not available with the permissions granted, the UI shows a dash (`—`) instead of a fabricated value.

## Download

Download the latest signed APK from the
[**Releases**](https://github.com/jrs8205/Device-Watch/releases/latest) page and open it on your
device to install. Because every release is signed with the same key, later versions install
cleanly as an update over an existing one.

Requires **Android 8.0 (API 26)** or newer.

## Architecture

The app follows an MVVM + repository structure with Hilt dependency injection.

```
presentation/   DashboardViewModel + AppsViewModel (StateFlow UI state)
presentation/ui Compose-only screen code: SystemDashboardScreen scaffold with a
                Material 3 NavigationBar, Overview/Apps/Device/Settings tabs and
                shared components (SettingsSectionCard, DeviceInfoRow, AppIcon,
                ScreenTimeDonut, AppDetailSheet)
data/           SystemStatsRepository + AppUsageRepository (per-app usage, on demand)
                AppSettingsRepository (data-counter mode, cycle start day, sort order)
                NotificationStats (daily notification counts, today + yesterday)
                SystemStatsParser, DataPeriodCalculator, UsageEventAggregator,
                NotificationCounting (pure, unit-tested calculations)
                SystemStats / AppUsage models (+ UNAVAILABLE_* sentinels)
widget/         Glance DashboardWidget, WidgetStateUpdater (DataStore writes),
                WidgetController (port the ViewModel talks to), receiver and actions
system/         SystemMonitorService (foreground), MonitorDreamService (screensaver),
                NotificationCounterService (notification listener)
di/             Hilt modules and entry points
```

- `SystemStatsRepositoryImpl` is the single source of truth. It is a `@Singleton`, reads system/kernel sources off the main thread on an injected dispatcher, and serializes its CPU-load snapshots with a `Mutex`.
- `SystemDashboardScreen` observes the ViewModel with `collectAsStateWithLifecycle()` and obtains it via `hiltViewModel()`. The three tabs are flat destinations switched with saveable tab state (no navigation library); each tab keeps its own scroll position. Permission requests and the foreground-service start stay at screen level.
- Services and the widget receiver use `@AndroidEntryPoint`; the Glance `ActionCallback` reaches the graph through a Hilt `EntryPoint`.

## Tech Stack

- Kotlin 2.4, AGP 9.2.1, Gradle 9.6
- `compileSdk 36`, `minSdk 26`, `targetSdk 35`
- Jetpack Compose (BOM 2026.06.00) + Material 3, Jetpack Glance 1.1.1
- Hilt 2.60 with KSP 2.3.9
- AndroidX Lifecycle 2.10, Activity 1.13, DataStore 1.2, WorkManager 2.11
- Dependencies are managed through the `gradle/libs.versions.toml` version catalog

## Language Behavior

Android selects the UI language from resource qualifiers:

- English and every non-Finnish device language use `app/src/main/res/values/strings.xml`
- Finnish devices use `app/src/main/res/values-fi/strings.xml`

## Permissions

The app requests only permissions that are used by the current feature set:

- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `NEARBY_WIFI_DEVICES`
- `READ_PHONE_STATE`
- `PACKAGE_USAGE_STATS`
- `QUERY_ALL_PACKAGES` (resolve names/icons for the per-app data list; the app is distributed outside Google Play)
- `REQUEST_DELETE_PACKAGES` (uninstall from the last-opened list via the system dialog)

Notification counting additionally uses the optional Notification access special permission (a `NotificationListenerService`); counting starts when access is granted. Do Not Disturb and Bluetooth control permissions are not requested.

## Building

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release builds are minified with R8 and resource shrinking. The release APK is unsigned unless a local `keystore.properties` and keystore are present.

## Testing

JVM unit tests cover the pure parsing/maths and the ViewModel:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

- `SystemStatsParserTest` — CPU-load deltas, frequency residency/pressure, battery wear, mobile-generation mapping, Wi-Fi SSID/band, signal filtering
- `DataPeriodCalculatorTest` — billing-cycle period math (start-day clamping across month lengths, leap February, year rollover)
- `UsageEventAggregatorTest` — foreground-session folding (in-app activity switches, unclosed sessions), donut segments, last-use sorting and day math
- `NotificationCountingTest` — the "real notification" filter and count retention/purging
- `WidgetFormattingTest` — widget display formatters (locale-pinned), adaptive MB/GB data amounts
- `DashboardViewModelTest` — refresh, opacity load/commit, data-counter settings, daily counters, widget-installed flag (hand-written fakes)
- `AppsViewModelTest` — Apps-tab loading, empty state without usage access, detail assembly, sort toggle persistence
- `ClockFitTest` — screensaver clock width-fit math
- `DreamLogicTest` — night-dim window (incl. crossing midnight) and charging-wattage normalization

## Build Outputs

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

APK and signing files are intentionally ignored by Git.

## Project Structure

```text
app/src/main/java/com/example/modernwidget/
  MainActivity.kt
  MonitorApp.kt
  data/
    AppSettingsRepository.kt
    AppSettingsRepositoryImpl.kt
    AppUsage.kt
    AppUsageRepository.kt
    AppUsageRepositoryImpl.kt
    DataPeriod.kt
    NotificationCounting.kt
    NotificationStats.kt
    NotificationStatsImpl.kt
    SystemStats.kt
    SystemStatsParser.kt
    SystemStatsRepository.kt
    SystemStatsRepositoryImpl.kt
    UsageEventAggregator.kt
  di/
    DispatchersModule.kt
    RepositoryModule.kt
    RepositoryEntryPoint.kt
  presentation/
    AppsViewModel.kt
    DashboardViewModel.kt
    ui/
      AppDetailSheet.kt
      AppsTab.kt
      DashboardComponents.kt
      DashboardTabs.kt
      DeviceTab.kt
      OverviewTab.kt
      ScreenTimeDonut.kt
      SettingsTab.kt
  system/
    BatteryFullNotifier.kt
    DreamPreferences.kt
    MonitorDreamService.kt
    NotificationCounterService.kt
    SystemMonitorService.kt
  widget/
    DashboardWidget.kt
    DashboardWidgetReceiver.kt
    RefreshStatsAction.kt
    WidgetController.kt
    WidgetStateUpdater.kt

app/src/test/java/com/example/modernwidget/
  data/DataPeriodCalculatorTest.kt
  data/NotificationCountingTest.kt
  data/SystemStatsParserTest.kt
  data/UsageEventAggregatorTest.kt
  presentation/AppsViewModelTest.kt
  presentation/DashboardViewModelTest.kt
  presentation/Fakes.kt
  system/ClockFitTest.kt
  system/DreamLogicTest.kt
  widget/WidgetFormattingTest.kt
```
