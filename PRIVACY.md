# Privacy Policy — Device Watch

**Last updated: 11 October 2026**

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
- **Device name** — Android's own name for the phone (as set under *About phone*), read only to show it above the screensaver clock; it is not stored or exported, and a switch in Settings hides it.
- **Camera and audio hardware facts** — lens details from the camera service and the list of connected audio devices, read without opening a camera or a microphone.
- **Device settings and hardware facts** — brightness, screen timeout, font and display size, developer options, power modes, screen lock, biometric sensors, NFC and similar read-only facts shown on the Device tab. `USE_BIOMETRIC` is used only to ask Android whether a strong biometric is enrolled (a yes/no); the app never authenticates you and never sees biometric data.
- **Extended access, only if you switch it on** (Shizuku or root; both off by default) — the app then runs commands in a shell with the ADB user's or root's rights. They only read, with three exceptions you choose separately. With *Start as soon as charging begins* on, one command asks Android to start the screensaver when charging begins, after reading which screensaver is selected and when it may start. With root only, never through Shizuku, a processor profile you choose on the Kernel page writes one kernel value for each group of processor cores, its top speed limit (`scaling_max_freq`), and nothing else: never above what the phone allows, the phone's own limits saved first and written back on *Stock* or when you switch root off (if root no longer answers then, they last until the next restart), and a restart returns them by itself unless you switch on *Apply after restart too*. The chosen profile and the saved limits stay on this phone and are not backed up. On the system apps page, a system app you choose to remove, after a question that names what breaks, is removed for your user alone (`pm uninstall --user`, which also clears that app's data for your user), and one you choose to restore is brought back the same way (`pm install-existing`); the page reads the package manager's record of each removed app (`dumpsys package`) only to leave off the ones your user never had, and keeps no list of its own. The reads: it reads kernel values (processor speeds and limits, processor and graphics load, temperatures, heat throttling, compressed memory, battery wear and dates) and Android's own battery statistics, which name the apps that used the battery and the wake locks they held, and Android's own record of which apps used the camera, the microphone and the location, along with the other things Android counts there (an accessibility service bound, device identifiers read, and contacts, calendar, message and call-log operations), when, for how long and, on Android 17, whether they were refused (the same record the system's Privacy dashboard shows, kept by Android for as long as Android keeps it); with root, also the kernel's own tally of its sleep attempts and of the wakeup sources that woke the phone or held it awake; and on Android 17, which no longer tells apps, whether developer options and USB debugging are on (the two switches Settings shows), and the battery's design capacity where the phone keeps that file from apps. These are shown on screen; apart from the latest values the widget displays, they are not stored: nothing from them is written to the history, the exports or a backup. Apart from these three, the app changes no system setting through this access, and the switch itself stays on this phone: it is not included in Android's backup or a transfer to a new phone.

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
