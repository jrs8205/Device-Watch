# Device Watch

[![Latest release](https://img.shields.io/github/v/release/jrs8205/Device-Watch?sort=semver)](https://github.com/jrs8205/Device-Watch/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/org.jarsi.devicewatch)](https://f-droid.org/packages/org.jarsi.devicewatch)
[![Aptoide](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fws75.aptoide.com%2Fapi%2F7%2Fapp%2FgetMeta%3Fpackage_name%3Dorg.jarsi.devicewatch&query=%24.data.file.vername&label=Aptoide&prefix=v&color=FE6446)](https://device-watch.en.aptoide.com/app)
[![Downloads](https://img.shields.io/github/downloads/jrs8205/Device-Watch/total)](https://github.com/jrs8205/Device-Watch/releases)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

Device Watch is an Android device monitoring app with Jetpack Glance home screen widgets, per-app usage insights (screen time, data, notifications and more), and an interactive screensaver for charging or docked use.

The default app language is English. Finnish users get a localized app name and UI through Android's `values-fi` resources.

## Screenshots

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="24%" alt="Home-screen widget" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="24%" alt="Overview tab" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="24%" alt="Screen-time donut" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="24%" alt="Since-charge view" />
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="24%" alt="Usage history" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="24%" alt="Most opened and top data" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="24%" alt="Last opened list" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="24%" alt="Charging screensaver" />
</p>

## Features

- Home screen widget for battery, memory, CPU, storage, Wi-Fi, mobile network, data usage, uptime, today's screen time, and last update time
- Compact 2×2 widget for battery, uptime and mobile/Wi-Fi data, with the counting period shown above each data amount
- Widget background opacity slider, and a black-background switch for people whose other widgets are black
- Tapping the widget anywhere opens the app
- Data counters per calendar day or per one-month billing cycle with a configurable start day (month lengths handled automatically); the selection applies to the widget and the in-app data rows
- Tabbed dashboard UI with swipe navigation between tabs: Home (live battery ring, usage counters, data counters and RAM/CPU/storage meters — full widget parity for people who skip the widget), Apps (usage insights), Device (hardware, SIM and Wi-Fi details), and Settings
- First-run intro that walks through the widget, screensaver, counters and history, with guided permission setup explaining what each permission unlocks; replayable any time from Settings
- Pull-to-refresh on every page, and haptic feedback on buttons
- Apps tab: Digital-Wellbeing-style screen-time donut (top apps + others) with tappable legend, top data consumers today, and a last-opened list (oldest and never-used apps first, reversible) with per-app uninstall; home-screen launchers excluded from usage rankings
- Per-app detail sheet: screen time, times opened, last opened, data used and notifications today, plus the app's version, install and update dates, where it was installed from, the Android version it targets and needs, and which sensitive permissions it has been allowed (location, camera, microphone and so on), with a shortcut to its system settings
- Live traffic meter on the Home tab: download and upload right now, with the last minute drawn as a line; it reads the counters only while the page is on screen
- Camera details on the Device tab: each lens with its megapixels, aperture, 35 mm equivalent focal length and optical stabilisation, the zoom range, RAW and manual-control support and the Camera2 level
- Audio on the Device tab: connected headphones, Bluetooth and USB audio devices, built-in microphones and spatial audio
- Usage counters on the Home tab, scoped to the same day/billing-cycle setting as the data counters: total screen time, screen unlocks (API 28+), a filtered notification count (ongoing notifications, group summaries and updates to an existing notification are not counted, so the number stays believable), device restarts and charging sessions
- The app keeps its own 62-day daily history for these counters (Android has no retroactive API): unlocks and screen time are backfilled from the ~7 days Android remembers, restarts are derived from BOOT_COUNT deltas (immune to Android re-delivering BOOT_COMPLETED after app updates), and notification/charging tallies accumulate from install onward
- On-device notification log with app name, timestamp, title, and text; 7-day retention; tapping an entry opens the app that posted it (when still installed)
- History page (opened from the usage card) listing exact daily values for the retained 62 days — screen time, unlocks, notifications, device restarts, and charging sessions; each metric states since when it has been collected, and the page refreshes itself while open
- Charges on the History page: every charge in the retained 14 days of battery history, with its start and end level, duration, charging rate and peak battery temperature
- Used storage per day on the History page, with the change from the previous reading, and in the CSV and HTML exports
- Screen-on time alongside app screen time, collected by the monitor service even when the dashboard stays closed (Android 9+)
- Battery chart with 14 days of history, charging intervals and visible collection gaps
- Export usage history and the notification log as CSV, or share a self-contained HTML report with charts, daily and monthly tables, search and date filters
- Optional charging reminder at a chosen battery level from 50 to 95 %, and mobile-data quota alerts at 80 % and 100 % of the selected day or billing-cycle allowance
- Optional alerts, all off by default: a hot battery (45 °C), low storage (under 10 % free) and a fast-draining battery (20 % an hour or more); each comes once and again only after things have returned to normal
- Monthly data usage history on the History page: metered mobile and Wi-Fi totals per calendar month, served straight from Android's own statistics for up to 12 months back (no local storage needed)
- Since-charge page (opened from the battery card): the period since the battery was last charged full — or since the charger was unplugged, when charging stopped short of full — with elapsed time, battery drop and average drain, unlocks, notifications, Wi-Fi/mobile data, and a per-app screen-time donut over that window (Android does not expose real per-app battery percentages to third-party apps, so without extended access the page shows honest usage numbers instead)
- Optional extended access through [Shizuku](https://shizuku.rikka.app/) (no root needed) or root, both off by default, for what Android hides from ordinary apps: Android's own battery statistics on the since-charge page (drain per hour with the screen on and off and how long a full battery would last at that pace, each app's share, and what kept the phone awake, explained in plain words), the real processor load, graphics load, processor/graphics/surface temperatures, and the battery's wear, charge cycles and dates where the phone keeps them
- Kernel page on rooted phones, opened from the Device tab's KERNEL band: each group of processor cores with its speed now, its limit and range, heat throttling in plain words, the graphics processor's speeds, compressed memory (zRAM), the kernel version, SELinux and the storage queue, and every named temperature sensor, all from what this phone's kernel reports, with no phone model assumed
- Processor profiles on rooted phones: *Power saving* (the faster cores at most about 60 % of their top speed), *Balanced* (about 80 %), *Stock*, or *Custom* per group of cores. Only speeds the kernel lists, never above what the phone allows; the phone's own limits are saved first and written back on Stock or when root is switched off (if root no longer answers then, they last until the next restart), and a restart returns them by itself unless *Apply after restart too* is on. When Android's power management raises a limit the monitor sets it again and counts it, and it never lifts a lower limit that heat or Android hold
- Privacy log with extended access (Shizuku or root), on the Apps tab's PRIVACY band and its page: Android's own record of which apps used the camera, the microphone and the location, when, for how long, from the background or not, and (on Android 17, which keeps them) which were refused, app by app and moment by moment; the same record the system's Privacy dashboard draws on, shown and never stored. Without extended access the band offers Android's own Privacy dashboard instead
- Sleep section on the Kernel page (root): the kernel's own count of sleep attempts and failures with what stopped the last one, the wakeup sources that woke the phone most often, those that held it awake longest, and those holding it right now, in plain words with the kernel's names underneath
- Today's screen time also appears in the widget footer, refreshed at most once a minute so the 5-second widget loop stays untouched
- Most-opened-today list on the Apps tab, and last-opened rows show the clock time for apps used today (following the system 12/24-hour setting) with two-tier staleness colors: amber after 1 month unused, red after 3 months (Google's app-hibernation threshold) or never used
- Special-access buttons show a green/red status dot for granted/missing access
- Active data-SIM carrier shown next to the mobile network name on dual-SIM devices
- Interactive Android screensaver with a large clock, date, next alarm, charging status, battery percentage, voltage, temperature, and live charging power in watts
- Screensaver clock follows the device 12/24-hour setting, with a second-aligned tick
- Battery-level-tinted background gradient and a softly pulsing charge indicator in the screensaver
- Optional screensaver dimming: manual, or automatic on a configurable night schedule (default 22:00–07:00)
- OLED burn-in protection: the screensaver content glides to a new spot within 15 dp once a minute, or, as a setting, only up and down within 20 dp so it stays centred sideways
- The phone's name (Android's device name, tidied and shortened, e.g. "Pixel 10 Pro") on a small line above the screensaver clock; can be turned off
- Optional start as soon as charging begins with the screen off, even with Always On Display on: at once through the dream manager with extended access, otherwise by waking the screen so the lock screen times out into the screensaver; nothing happens while the screen is on, and Android's own screensaver choice and "when to start" are respected
- Icons of unread notifications under the screensaver clock (one per app, with a count), pulsing when a new one arrives; needs notification access and can be turned off in Settings
- Remembered screensaver rotation setting for repeated charging sessions; the background gradient mirrors with the 180° layout swap
- Larger screensaver rotation touch target for easier use
- Battery full notification while the screensaver is active
- English default resources with Finnish localization
- Runtime permission handling for location, phone state, nearby Wi-Fi devices, and notifications
- Usage Access shortcut for network and app-usage statistics
- Release build configured with R8 minification and resource shrinking

Every metric is real data read from Android and kernel sources. When a value is not available with the permissions granted, the UI shows a dash (`—`) instead of a fabricated value.

Android backup and phone transfer carry user settings only. History, notification texts and delivered-alert flags stay on the device; a new phone can show its own first alerts. Exports leave the app only when you choose to share them.

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/org.jarsi.devicewatch)
[<img src="assets/get-it-on-aptoide.svg" alt="Download from Aptoide" height="80">](https://aptoide-mmp.aptoide.com/api/v1/download?package_name=org.jarsi.devicewatch&oemid=584d342f9e68b0c33244b78f3cc21a24&redirect_url=https%3A%2F%2Fws.catappult.io%2Fapi%2FdirectToConsumer%2Forg.jarsi.devicewatch%2Fdownload)

Device Watch is available on
[**F-Droid**](https://f-droid.org/packages/org.jarsi.devicewatch), which also delivers updates
automatically, and on [**Aptoide**](https://device-watch.en.aptoide.com/app), which also offers a
[direct APK download](https://aptoide-mmp.aptoide.com/api/v1/download?package_name=org.jarsi.devicewatch&oemid=584d342f9e68b0c33244b78f3cc21a24&redirect_url=https%3A%2F%2Fws.catappult.io%2Fapi%2FdirectToConsumer%2Forg.jarsi.devicewatch%2Fdownload).
Alternatively, download the latest signed APK from the
[**Releases**](https://github.com/jrs8205/Device-Watch/releases/latest) page, or from its mirror on
[**SourceForge**](https://sourceforge.net/projects/device-watch/), and open it on your device to
install. Releases are built reproducibly and F-Droid verifies each build against the
developer-signed APK, so every source ships an APK signed with the same key and later versions
install cleanly as an update over an existing one — from any source.

**Registered with Google.** The package name and signing key are registered in Google's Android
Developer Console, so the app keeps installing as usual under Google's new [sideloading
rules](https://developer.android.com/developer-verification).

> **Upgrading from v1.3.1 or older:** the application ID changed in v1.4.0 from
> `com.example.modernwidget` to `org.jarsi.devicewatch`, so Android treats it as a new app.
> Install the new version, re-grant its permissions, re-add the widget and re-select the
> screensaver, then uninstall the old one. Collected usage history starts fresh.

Requires **Android 8.0 (API 26)** or newer.

## Troubleshooting

Common questions — the screensaver not starting, mobile data showing zero, notification counts,
history retention — are answered in the [FAQ](FAQ.md).

## Architecture

The app follows an MVVM + repository structure with Hilt dependency injection.

```
presentation/   DashboardViewModel, AppsViewModel, HistoryViewModel, SinceChargeViewModel
                and LiveTrafficViewModel (StateFlow UI state); CsvExporter,
                HtmlReportBuilder and PeriodComparison behind the History page
presentation/ui Compose-only screen code: SystemDashboardScreen scaffold with a
                Material 3 NavigationBar, Home/Apps/Device/Settings tabs, the
                History and Since-charge pages, the first-run intro, BatteryChart,
                LiveTrafficMeter and shared components (SettingsSectionCard,
                DeviceInfoRow, AppIcon, ScreenTimeDonut, AppDetailSheet, MetricText)
ui/theme/       The two looks (high-contrast and classic) as full Material 3 schemes
data/           SystemStatsRepository + AppUsageRepository (per-app usage, on demand)
                AppSettingsRepository (data-counter mode, cycle start day, alert switches)
                NotificationStats, UsageHistory, BatteryHistory (own tallies: 62-day
                daily history, 14-day battery log, charge sessions)
                SystemStatsParser, DataPeriodCalculator, UsageEventAggregator,
                NotificationCounting, ChargeSessions, AlertLogic, DataQuotaLogic,
                Camera/Audio/AppPackage logic (pure, unit-tested calculations)
                SystemStats / AppUsage / DeviceInfo models (+ UNAVAILABLE_* sentinels)
widget/         Glance DashboardWidget and CompactWidget, WidgetStateUpdater (DataStore
                writes), WidgetController (port the ViewModel talks to), receivers,
                actions, and the font cap and the fit to the widget's height
                (WidgetTextScale)
system/         SystemMonitorService (foreground) with the charge-limit, data-quota and
                health-alert notifiers, MonitorDreamService (screensaver) with
                DreamNotifications, NotificationCounterService (notification listener)
di/             Hilt modules and entry points
```

- `SystemStatsRepositoryImpl` is the single source of truth. It is a `@Singleton`, reads system/kernel sources off the main thread on an injected dispatcher, and serializes its CPU-load snapshots with a `Mutex`.
- `SystemDashboardScreen` observes the ViewModels with `collectAsStateWithLifecycle()` and obtains them via `hiltViewModel()`. The four tabs are pages of a `HorizontalPager` whose state is the single source of truth for the selected tab — swiping and the bottom bar both drive it (no navigation library); each page keeps its own scroll position. Permission requests and the foreground-service start stay at screen level.
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
- `WAKE_LOCK` (a normal permission, granted on install; WorkManager declares it for its own jobs, and the app's own code uses it only to wake the screen when charging begins, so the screensaver can start, when *Start as soon as charging begins* is switched on)
- `USE_BIOMETRIC` (a normal permission, granted on install: only to show on the Device tab whether a strong biometric is enrolled — the app never authenticates anyone)
- `moe.shizuku.manager.permission.API_V23` (declared by the Shizuku library; it does nothing unless you install Shizuku yourself, switch Shizuku on in Settings and approve Device Watch in Shizuku's own dialog)

Root mode needs no permission: it is off by default, and the app starts `su` only after you switch it on and your root manager grants it. Through root alone, a processor profile you choose on the Kernel page writes each group of cores' top speed limit; nothing else is written.

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

1022 JVM unit tests (JUnit 4 + Truth; Robolectric only for the Glance render, backup-rule and
settings-store tests) cover the pure logic behind every screen, service and widget:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

- **Parsing and maths** — `SystemStatsParserTest`, `CpuWindowTest`, `DataPeriodCalculatorTest`,
  `UsageEventAggregatorTest`, `ExtraStatsLogicTest`, `AppPackageLogicTest`, `CameraLogicTest`,
  `AudioLogicTest`, `TrafficMeterTest`, `DefaultDensityTest` (when Android's default density can
  be trusted)
- **Extended access (Shizuku and root)** — `ShellSessionTest` (the shell conversation, driven
  through a real `sh`; on Windows through Git's), `PrivilegedShellTest`,
  `BatteryStatsDumpParserTest`, `PrivilegedDumpParsersTest` (parsers written against dumps from
  real phones), `AppOpsHistoryParserTest` (Android's privacy record in both the table Android 17
  prints and the tree of Android 12–16, against a Pixel 8a and API 34 and 36 emulators),
  `PrivacyLogBuilderTest`, `ShellPrivacyLogSourceTest`, `PrivacyPresentationTest`,
  `PrivacyViewModelTest`, `LocationGatedReadsTest` (why the app no longer appears in its own log)
- **Kernel page and processor profiles (root)** — `KernelSysfsParserTest` (against a full read
  from a Pixel 8a), `KernelSnapshotTest`, `KernelCommandsTest`, `RootOnlyShellTest`, `KernelSnapshotSourceTest`,
  `CpuProfilePlannerTest`, `CpuProfileRulesTest`, `CpuProfileStoreTest`, `CpuLimitWriterTest`,
  `CpuProfileControllerTest` (over `FakeSysfsRoot`, a pretend cpufreq tree with the kernel's
  frequency QoS, Android's power HAL, heat and switched-off cores), `KernelRootOnlyGuardTest`,
  `KernelPresentationTest`, `KernelViewModelTest`, `WakeReadTest` (the sleep section's command, parser
  and root gate against a Pixel 8a's wakeup sources), `WakePresentationTest`, `KernelViewModelWakeTest`
- **Own history and stores** — `UsageHistoryLogicTest`, `UsageHistoryImplTest`,
  `NotificationCountingTest`, `NotificationLogCodecTest`, `NotificationLogImplTest`,
  `BatteryHistoryCodecTest`, `BatteryHistoryImplTest`, `ChargeSessionsTest`,
  `ChargeAnchorLogicTest`, `AppSettingsRepositoryImplTest`, `DeviceLocalFlagsTest`,
  `AlertStateBackupTest`, `BackupRulesTest`
- **Alerts and notifications** — `AlertLogicTest`, `DataQuotaLogicTest`,
  `DataQuotaSettingsChangeTest`, `ChargeLimitLogicTest`, `HealthAlertControllerTest`,
  `NotificationDeliveryTest`
- **ViewModels and exports** — `DashboardViewModelTest`, `AppsViewModelTest`,
  `HistoryViewModelTest`, `SinceChargeViewModelTest`, `LiveTrafficViewModelTest`,
  `PeriodComparisonTest`, `CsvExporterTest`, `HtmlReportBuilderTest`, `ReportPaletteTest`
  (hand-written fakes in `Fakes.kt`)
- **UI logic** — `HistoryListLogicTest`, `SinceChargeNoticesTest`, `BatteryChartLogicTest`,
  `LabelValueRowTest`, `AppScaleTest`, `AppPaletteTest` (the WCAG AAA contrast of both looks)
- **Widgets** — `WidgetFormattingTest`, `WidgetTextScaleTest` (sizes in plain dp, the fit to
  the widget's height), `CompactFitTest` (text fitting, the floor for names and the ellipsis),
  `CompactUptimeTest`, `CompactWidgetRenderTest`
- **Screensaver** — `ClockFitTest`, `DreamLogicTest` (night-dim window, charging watts),
  `DreamNotificationLogicTest` (which notifications show, grouping, arrivals, row fit),
  `ActiveNotificationsStoreTest`, `BurnInShiftTest`, `DeviceNameTest` (tidying, shortening,
  model codes left out), `ChargeStartLogicTest` (when charging starts the screensaver, and how),
  `ChargeStartCommandTest` (the start command's own check of the screen, through a real `sh`)

## Build Outputs

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

APK and signing files are intentionally ignored by Git.

## Project Structure

```text
app/src/main/java/org/jarsi/devicewatch/
  MainActivity.kt
  MonitorApp.kt
  data/
    AlertLogic.kt
    AppOpsHistory.kt
    AppOpsHistoryParser.kt
    AppPackageLogic.kt
    AppSettingsRepository.kt
    AppSettingsRepositoryImpl.kt
    AppUsage.kt
    AppUsageRepository.kt
    AppUsageRepositoryImpl.kt
    AppVisibility.kt
    AudioLogic.kt
    BatteryHistory.kt
    BatteryHistoryImpl.kt
    BatteryStatusReader.kt
    BatteryUsage.kt
    CameraLogic.kt
    ChargeAnchor.kt
    ChargeAnchorStoreImpl.kt
    ChargeSessions.kt
    CpuLimitWriter.kt
    CpuProfilePlanner.kt
    CpuProfileRules.kt
    CpuProfileStore.kt
    CpuProfiles.kt
    DataPeriod.kt
    DataQuotaLogic.kt
    DeviceInfo.kt
    DeviceLocalFlags.kt
    DeviceState.kt
    ExtraStatsLogic.kt
    KernelCommands.kt
    KernelSnapshot.kt
    KernelSnapshotSource.kt
    KernelSysfsParser.kt
    LocationGatedReads.kt
    NotificationCounting.kt
    NotificationLog.kt
    NotificationLogImpl.kt
    NotificationStats.kt
    NotificationStatsImpl.kt
    PrivacyLog.kt
    PrivacyLogSource.kt
    PrivilegedDumpParsers.kt
    PrivilegedShell.kt
    RootCommandShell.kt
    RootShell.kt
    ShellBatteryUsageSource.kt
    ShellUserService.kt
    ShizukuShell.kt
    SystemStats.kt
    SystemStatsParser.kt
    SystemStatsRepository.kt
    SystemStatsRepositoryImpl.kt
    TrafficMeter.kt
    UsageEventAggregator.kt
    UsageHistory.kt
    UsageHistoryImpl.kt
    WakeSnapshot.kt
  di/
    DispatchersModule.kt
    RepositoryEntryPoint.kt
    RepositoryModule.kt
  presentation/
    AppsViewModel.kt
    CsvExporter.kt
    DashboardViewModel.kt
    HistoryCoverage.kt
    HistoryViewModel.kt
    HtmlReportBuilder.kt
    KernelPresentation.kt
    KernelViewModel.kt
    LiveTrafficViewModel.kt
    PeriodComparison.kt
    PrivacyPresentation.kt
    PrivacyViewModel.kt
    SinceChargeViewModel.kt
    WakePresentation.kt
    ui/
      AppDetailSheet.kt
      AppScale.kt
      AppsTab.kt
      BatteryChart.kt
      BatteryChartLogic.kt
      BatteryUsageCards.kt
      DashboardComponents.kt
      DashboardTabs.kt
      DeviceTab.kt
      Haptics.kt
      HistoryListLogic.kt
      HistoryPage.kt
      KernelBand.kt
      KernelPage.kt
      KernelSleepSection.kt
      LiveTrafficMeter.kt
      MetricText.kt
      OnboardingPage.kt
      OverviewTab.kt
      PrivacyBand.kt
      PrivacyDashboardIntent.kt
      PrivacyPage.kt
      ScreenTimeDonut.kt
      SettingsTab.kt
      SinceChargeNotices.kt
      SinceChargePage.kt
  system/
    AlertNotifications.kt
    BatteryFullNotifier.kt
    ChargeLimitLogic.kt
    ChargeLimitNotifier.kt
    DataQuotaNotifier.kt
    DreamNotifications.kt
    DreamPreferences.kt
    HealthAlertController.kt
    HealthAlertNotifier.kt
    MonitorDreamService.kt
    MonitorServiceRelay.kt
    NotificationCounterService.kt
    NotificationDelivery.kt
    SystemMonitorService.kt
  ui/
    theme/
      Color.kt
      Theme.kt
  widget/
    CompactWidget.kt
    DashboardWidget.kt
    DashboardWidgetReceiver.kt
    RefreshStatsAction.kt
    WidgetController.kt
    WidgetStateUpdater.kt
    WidgetTextScale.kt

app/src/test/java/org/jarsi/devicewatch/
  data/
    AlertLogicTest.kt
    AlertStateBackupTest.kt
    AppOpsFixtures.kt
    AppOpsHistoryParserTest.kt
    AppPackageLogicTest.kt
    AppSettingsRepositoryImplTest.kt
    AudioLogicTest.kt
    BackupRulesTest.kt
    BatteryHistoryCodecTest.kt
    BatteryHistoryImplTest.kt
    BatteryStatsDumpParserTest.kt
    CameraLogicTest.kt
    ChargeAnchorLogicTest.kt
    ChargeSessionsTest.kt
    CpuLimitWriterTest.kt
    CpuProfileControllerTest.kt
    CpuProfilePlannerTest.kt
    CpuProfileRulesTest.kt
    CpuProfileStoreTest.kt
    CpuWindowTest.kt
    DataPeriodCalculatorTest.kt
    DataQuotaLogicTest.kt
    DeviceLocalFlagsTest.kt
    ExtraStatsLogicTest.kt
    FakeSysfsRoot.kt
    KernelCommandsTest.kt
    KernelFixtures.kt
    KernelRootOnlyGuardTest.kt
    KernelSnapshotSourceTest.kt
    KernelSnapshotTest.kt
    KernelSysfsParserTest.kt
    LocationGatedReadsTest.kt
    NotificationCountingTest.kt
    NotificationLogCodecTest.kt
    NotificationLogImplTest.kt
    PrivacyLogBuilderTest.kt
    PrivilegedDumpParsersTest.kt
    PrivilegedShellTest.kt
    RootOnlyShellTest.kt
    ShellPrivacyLogSourceTest.kt
    ShellSessionTest.kt
    SystemStatsParserTest.kt
    TrafficMeterTest.kt
    UsageEventAggregatorTest.kt
    UsageHistoryImplTest.kt
    UsageHistoryLogicTest.kt
    WakeFixtures.kt
    WakeReadTest.kt
  presentation/
    AppsViewModelTest.kt
    CsvExporterTest.kt
    DashboardViewModelTest.kt
    Fakes.kt
    HistoryViewModelTest.kt
    HtmlReportBuilderTest.kt
    KernelPresentationTest.kt
    KernelViewModelTest.kt
    KernelViewModelWakeTest.kt
    LiveTrafficViewModelTest.kt
    PeriodComparisonTest.kt
    PrivacyPresentationTest.kt
    PrivacyViewModelTest.kt
    ReportPaletteTest.kt
    SinceChargeViewModelTest.kt
    WakePresentationTest.kt
    ui/
      AppScaleTest.kt
      BatteryChartLogicTest.kt
      HistoryListLogicTest.kt
      LabelValueRowTest.kt
      SinceChargeNoticesTest.kt
  system/
    ActiveNotificationsStoreTest.kt
    ChargeLimitLogicTest.kt
    ClockFitTest.kt
    DataQuotaSettingsChangeTest.kt
    DreamLogicTest.kt
    DreamNotificationLogicTest.kt
    HealthAlertControllerTest.kt
    NotificationDeliveryTest.kt
  ui/
    theme/
      AppPaletteTest.kt
  widget/
    CompactFitTest.kt
    CompactUptimeTest.kt
    CompactWidgetRenderTest.kt
    WidgetFormattingTest.kt
    WidgetTextScaleTest.kt
```

## License

Device Watch is free software, licensed under the GNU General Public License,
version 3 of the License, or (at your option) any later version
(SPDX: `GPL-3.0-or-later`). See [LICENSE](LICENSE) for the full text.
