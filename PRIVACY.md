# Privacy Policy — Device Watch

**Last updated: 28 September 2026**

Device Watch (`org.jarsi.devicewatch`) is a device monitoring app developed by Jarsi Sode.

## The short version

**Device Watch does not upload your personal data.** The app has no `INTERNET` permission and makes no network connections. There are no analytics, no advertising, no tracking, and no third-party SDKs that process data. You can choose to share exports through another app, as described below.

## What the app reads and why

To show its statistics, Device Watch reads the following information **locally on your device**:

- **Battery, memory, CPU, storage and uptime** — shown on the widget, dashboard and screensaver.
- **App usage statistics** (requires the *Usage access* special permission you grant manually) — used for screen-time, last-opened and since-charge views.
- **Notifications** (requires the *Notification access* special permission you grant manually) — used only to count notifications, keep an on-device notification log with a 7-day retention, and show the small icons of unread notifications on the charging screensaver (icons only, never their text; off by a switch in Settings). Notification content is excluded from Android backup; it is included in exports only when you choose to share the log or HTML report.
- **Network state, Wi-Fi details and SIM information** (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `READ_PHONE_STATE`, location permissions) — Android requires the location permission to reveal the Wi-Fi network name (SSID) and the band and channel of the mobile cell the phone is registered to. It is used solely to display those; the app does not access or store your geographic location.
- **Installed application list** (`QUERY_ALL_PACKAGES`) — needed to resolve app names and icons for the usage statistics, the per-app storage sizes and the list of apps exempt from battery optimisation. `REQUEST_DELETE_PACKAGES` is used only to open the standard Android uninstall dialog when you tap *Uninstall*.
- **Battery temperature** — stored with each battery-history sample (14 days) to show the warmest point of each charge, and compared with 45 °C when you switch the hot-battery alert on.
- **Camera and audio hardware facts** — lens details from the camera service and the list of connected audio devices, read without opening a camera or a microphone.
- **Device settings and hardware facts** — brightness, screen timeout, font and display size, developer options, power modes, screen lock, biometric sensors, NFC and similar read-only facts shown on the Device tab. `USE_BIOMETRIC` is used only to ask Android whether a strong biometric is enrolled (a yes/no); the app never authenticates you and never sees biometric data.

## Where the data lives

All statistics (usage history up to 62 days including the daily used storage, notification log up to 7 days, battery and charge history) are stored in the app's private storage on your device. They are deleted when you uninstall the app or clear its data. Nothing is uploaded, synced, or backed up to any server by the app.

Android's own backup (Google's cloud backup and the transfer to a new phone, where you use them) may include the app's **settings only**: the counting period, data quota, charge limit, alert switches, look and screensaver options. The statistics, the battery and charge history, the notification log, permission/intro flags and records of alerts already delivered are excluded. Alert choices can transfer to a new phone without suppressing that phone's first alert.

## Exports you choose to share

The History page can create CSV files and a self-contained HTML report. The notification-log CSV and HTML report include notification titles and text. Files are created in the app's private cache and shared through Android's share sheet only at your request. The receiving app controls what happens to your shared copy; it may save or upload it. Old export files are removed from the cache during later exports after 24 hours, but this cannot delete copies saved elsewhere.

## Changes

If the data practices of the app ever change, this document will be updated in the same repository before the change ships.

## Contact

Questions about this policy: **jarsi@jarsi.org** or open an issue at
<https://github.com/jrs8205/Device-Watch/issues>.
