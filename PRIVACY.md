# Privacy Policy — Device Watch

**Last updated: 12 July 2026**

Device Watch (`org.jarsi.devicewatch`) is a device monitoring app developed by Jarsi Sode.

## The short version

**Device Watch does not collect, store, or transmit any personal data off your device.** The app has no `INTERNET` permission, so it is technically incapable of sending anything anywhere. There are no analytics, no advertising, no tracking, and no third-party SDKs that process data.

## What the app reads and why

To show its statistics, Device Watch reads the following information **locally on your device**:

- **Battery, memory, CPU, storage and uptime** — shown on the widget, dashboard and screensaver.
- **App usage statistics** (requires the *Usage access* special permission you grant manually) — used for screen-time, last-opened and since-charge views.
- **Notifications** (requires the *Notification access* special permission you grant manually) — used only to count notifications and keep an on-device notification log with a 7-day retention. Notification content never leaves the device.
- **Network state, Wi-Fi details and SIM information** (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `READ_PHONE_STATE`, location permissions) — Android requires the location permission to reveal the Wi-Fi network name (SSID). It is used solely to display the SSID; the app does not access or store your geographic location.
- **Installed application list** (`QUERY_ALL_PACKAGES`) — needed to resolve app names and icons for the usage statistics. `REQUEST_DELETE_PACKAGES` is used only to open the standard Android uninstall dialog when you tap *Uninstall*.

## Where the data lives

All statistics (usage history up to 62 days, notification log up to 7 days, charge history) are stored in the app's private storage on your device. They are deleted when you uninstall the app or clear its data. Nothing is uploaded, synced, or backed up to any server by the app.

## Changes

If the data practices of the app ever change, this document will be updated in the same repository before the change ships.

## Contact

Questions about this policy: **jarsi@jarsi.org** or open an issue at
<https://github.com/jrs8205/Device-Watch/issues>.
