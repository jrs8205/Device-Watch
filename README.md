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

## Language Behavior

Android selects the UI language from resource qualifiers:

- English and every non-Finnish device language use `app/src/main/res/values/strings.xml`
- Finnish devices use `app/src/main/res/values-fi/strings.xml`

Finnish strings in `values-fi` are intentional localization resources. Repository documentation and default app strings are English.

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
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release builds are minified with R8 and resource shrinking. The default release APK is unsigned unless a release signing configuration is added locally.

## Build Outputs

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Unsigned release APK:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

APK files are intentionally ignored by Git.

## Project Structure

```text
app/src/main/java/com/example/modernwidget/
  MainActivity.kt
  system/
    BatteryFullNotifier.kt
    MonitorDreamService.kt
    SystemMonitorService.kt
    SystemStatsHelper.kt
  widget/
    DashboardWidget.kt
    DashboardWidgetReceiver.kt
    LaunchSettingsAction.kt
    RefreshStatsAction.kt
    WidgetStateUpdater.kt

app/src/main/res/
  values/strings.xml
  values-fi/strings.xml
  drawable/
  xml/
```
