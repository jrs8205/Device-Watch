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

### No notification icons show on the screensaver

The row of icons under the clock needs the **Notification access** special permission, the same
one the notification counter uses; the Settings tab's *Special access* section has the shortcut.
Only notifications you can still clear are shown (nothing ongoing, no group summaries), one icon
per app with a count, and the row can be turned off with *Show notification icons* in Settings.

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

With extended access switched on (Shizuku or root, see below), the same page also shows Android's
own battery statistics: each app's share, the drain with the screen on and off, and what kept the
phone awake.

## Extended access (Shizuku and root)

### What is it, and do I need it?

No. Everything else in the app works without it. It is an optional switch in Settings for
readings Android hides from ordinary apps: Android's own battery statistics (per-app drain,
drain per hour with the screen on and off, wake locks), the real processor load, the graphics
load, temperatures by part, and the battery's wear, charge cycles and dates where the phone
keeps them.

### How do I use it without root?

Install [Shizuku](https://shizuku.rikka.app/), start it as its own instructions say (through
wireless debugging on Android 11 and newer), then switch **Shizuku** on in Device Watch's Settings
and allow Device Watch in the dialog Shizuku shows. Shizuku gives the app the same rights the
`adb shell` command has; nothing on the phone is modified.

### The extended readings disappeared after a restart

Without root, Shizuku stops whenever the phone restarts and has to be started again from the
Shizuku app. The switch in Device Watch stays on and says it is waiting; the readings return once
Shizuku runs. The permission you gave is remembered.

### What does root mode add?

On a rooted phone the **Root mode** switch reads the same things through `su`, plus the few
kernel values even Shizuku is denied on some phones. Your root manager asks for permission once.
The app keeps one root shell open instead of starting a new one for every reading, so the root
manager is not flooded with requests.

### Root mode says access was refused, and no prompt appears

Magisk remembers a refusal: pressing Back on its prompt, letting it time out or tapping Deny
stores a lasting Deny for the app, and later attempts are refused without a prompt. Open
Magisk's Superuser list, allow Device Watch there, then switch Root mode on again. KernelSU and
APatch never prompt at all: allow Device Watch in their manager first.

If a grant is revoked later, Device Watch tries a few more times over about ten minutes, then
switches Root mode off and says so, rather than keep asking.

### Why do some rows not appear on my phone?

Each maker exposes different things. A Samsung, for example, hides the battery's kernel values
even from Shizuku but tells its wear and cycle count through its battery service; a Pixel does
the opposite. A row is shown only when the phone gives a real value.

### Is the drain per hour reliable?

It is Android's own measurement, counted in whole mAh steps, so a pace is shown only for a
stretch of at least ten minutes. With the screen off, under about 1 % an hour means the phone
sleeps well; clearly more than 2.5 % an hour means something keeps it awake, and the list of
what kept the phone awake usually names it.

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

### F-Droid, Aptoide or the GitHub APK?

Any of them. Releases are built reproducibly and F-Droid verifies its own build against the
developer-signed APK, so every source ships an APK signed with the same key — you can install from
one and later update from another. F-Droid and Aptoide additionally deliver updates automatically.

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
