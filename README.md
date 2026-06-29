# Device Watch

Device Watch is an Android device monitoring app with a Jetpack Glance home screen widget and an interactive screensaver for charging or docked use.

The default app language is English. Finnish users get a localized app name and UI through Android's `values-fi` resources.

## Features

- Home screen widget for battery, memory, CPU, storage, Wi-Fi, mobile network, data usage, battery health, uptime, and last update time
- Interactive Android screensaver with a large clock, date, charging status, battery percentage, voltage, and temperature
- Remembered screensaver rotation setting for repeated charging sessions
- Larger screensaver rotation touch target for easier use
- Battery full notification while the screensaver is active
- English default resources with Finnish localization
- Runtime permission handling for location, phone state, nearby Wi-Fi devices, and notifications
- Usage Access shortcut for daily network usage statistics
- Release build configured with R8 minification and resource shrinking

Every metric is real data read from Android and kernel sources. When a value is not available with the permissions granted, the UI shows a dash (`—`) instead of a fabricated value.

## Architecture

The app follows an MVVM + repository structure with Hilt dependency injection.

```
presentation/   DashboardViewModel + DashboardUiState (StateFlow), consumed by MainActivity
data/           SystemStatsRepository (interface) + SystemStatsRepositoryImpl (@Singleton)
                SystemStatsParser (pure, unit-tested calculations)
                SystemStats (immutable model + UNAVAILABLE_* sentinels)
widget/         Glance DashboardWidget, WidgetStateUpdater (DataStore writes),
                WidgetController (port the ViewModel talks to), receiver and actions
system/         SystemMonitorService (foreground), MonitorDreamService (screensaver)
di/             Hilt modules and entry points
```

- `SystemStatsRepositoryImpl` is the single source of truth. It is a `@Singleton`, reads system/kernel sources off the main thread on an injected dispatcher, and serializes its CPU-load snapshots with a `Mutex`.
- `MainActivity` is a single Compose screen that observes the ViewModel with `collectAsStateWithLifecycle()` and obtains it via `hiltViewModel()`. Permission requests and the foreground-service start stay in the Activity.
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

Do Not Disturb and Bluetooth control permissions are not requested.

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
- `WidgetFormattingTest` — widget display formatters (locale-pinned)
- `DashboardViewModelTest` — refresh, opacity load/commit, widget-installed flag (fake repository + fake widget controller)

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
    SystemStats.kt
    SystemStatsParser.kt
    SystemStatsRepository.kt
    SystemStatsRepositoryImpl.kt
  di/
    DispatchersModule.kt
    RepositoryModule.kt
    RepositoryEntryPoint.kt
  presentation/
    DashboardViewModel.kt
  system/
    BatteryFullNotifier.kt
    MonitorDreamService.kt
    SystemMonitorService.kt
  widget/
    DashboardWidget.kt
    DashboardWidgetReceiver.kt
    LaunchSettingsAction.kt
    RefreshStatsAction.kt
    WidgetController.kt
    WidgetStateUpdater.kt

app/src/test/java/com/example/modernwidget/
  data/SystemStatsParserTest.kt
  presentation/DashboardViewModelTest.kt
  widget/WidgetFormattingTest.kt
```
