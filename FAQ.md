# Frequently Asked Questions

Troubleshooting and background for questions that come up when using Device Watch. For features,
permissions and building, see the [README](README.md).

## Screensaver

### The screensaver never starts while charging

Android only starts a screensaver while the device is charging (or docked) **and the screen times
out by itself**. Two things routinely trip people up:

- **Pressing the power button never starts the screensaver** — that just puts the screen to
  sleep. Plug the charger in, leave the device alone and let the screen timeout expire.
- Device Watch must be selected as the system screensaver and "start while charging" enabled.
  The app's Settings tab has an *Open screensaver settings* shortcut that takes you straight
  there; the exact system path varies by manufacturer.

### It still doesn't start on my Samsung

One UI's **Always On Display takes precedence over screensavers**: with AOD enabled, the system
shows AOD when the screen goes off while charging, even when the screensaver is correctly
selected. Turn AOD off (or set it to tap-to-show) to let the screensaver run.

### The screensaver is too bright at night

The Settings tab has an optional dim toggle, plus an automatic night dim with a configurable
schedule (default 22:00–07:00).

## Data usage

### Mobile data shows zero, or far less than my carrier reports

Android's public statistics API only reports **metered** mobile traffic. If your SIM or plan is
flagged unmetered — common with unlimited data plans — its traffic is invisible to every
third-party app. Device Watch shows exactly what Android reports rather than guessing.

### The Wi-Fi network name shows a dash

Reading the SSID requires the location permission **and location services turned on** — the
permission alone is not enough. This is an Android privacy rule, not an app choice.

## Battery

### Why is there no per-app battery percentage?

Android does not expose per-app battery consumption to third-party apps (it requires a privileged
system permission). Rather than invent percentages, the since-charge page shows real usage over
the period: per-app screen time, data, unlocks and notifications.

### The since-charge page is empty

The page starts tracking from the first charge event after installation — either the battery
reaching full on the charger, or the charger being unplugged. It fills in from that point on.

## Counters and history

### The notification count is lower than the number of notifications I see

The count is filtered on purpose so it stays believable: ongoing notifications (media playback,
navigation, persistent status notifications), group summaries and updates to an already-posted
notification are not counted. The notification log applies the same filter, so the two always
agree.

### How far back does the history go?

The app keeps its own daily history for **62 days**. What can be shown from before installation
differs per metric, and the History page states "collected since" for each one:

- Data counters are fully retroactive — Android itself provides them.
- Screen time and unlocks are backfilled from the roughly 7 days Android remembers.
- Notification and charging tallies accumulate from installation onward.

## Installation and updates

### F-Droid or the GitHub APK?

Either. Releases are built reproducibly and F-Droid verifies its own build against the
developer-signed APK, so both sources ship an APK signed with the same key — you can install from
one and later update from the other. F-Droid additionally delivers updates automatically.

### Updating from v1.3.1 or older

The application ID changed in v1.4.0 from `com.example.modernwidget` to `org.jarsi.devicewatch`,
so Android treats it as a new app. Install the new version, re-grant its permissions, re-add the
widget and re-select the screensaver, then uninstall the old one. Collected usage history starts
fresh.

### The monitoring notification icon won't go away

The foreground monitoring service must show a notification — an Android requirement. Device Watch
posts it silent and at minimum importance, but some devices (notably One UI) still keep a
status-bar icon. To hide it completely, long-press the notification and minimize it, or turn its
channel off in the system notification settings; monitoring keeps running either way.
