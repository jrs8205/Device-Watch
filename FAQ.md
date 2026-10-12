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
- **With Always On Display on, plugging in does not wake the phone**, so there is no timeout and
  the screensaver waits until someone wakes the screen. Switch on *Start as soon as charging
  begins* in the app's Settings: Device Watch then wakes the screen when charging begins with the
  screen off, and the lock screen times out into the screensaver in about 10–15 seconds. With
  extended access (Shizuku or root) on, it asks Android to start the screensaver at once instead.

### It still doesn't start on my Samsung

One UI's **Always On Display takes precedence over screensavers**: with AOD enabled, the system
shows AOD when the screen goes off while charging, even when the screensaver is correctly
selected. Turn AOD off (or set it to tap-to-show) to let the screensaver run.

### The screensaver sits off-centre sideways

To protect the screen from burn-in, the screensaver moves a little once a minute. Switch on
*Move only up and down* in the app's Settings to keep it centred sideways.

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
kernel values even Shizuku is denied on some phones, and it adds the Kernel page with its
processor profiles (below). Your root manager asks for permission once.
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

### What is the Kernel page?

With root mode on, the Device tab starts with a **KERNEL** band; **Open** leads to the page. It
shows what this phone's kernel itself reports: each group of processor cores with its speed now,
its limit and its range, whether heat is slowing anything down, the graphics processor's speeds,
compressed memory (zRAM), the kernel version, SELinux, the storage queue and every named
temperature sensor. Nothing is assumed from the phone's model, so what you see is what your
kernel offers. The page and the band read the kernel every few seconds only while they are on
screen; with a profile on, the monitor also checks the held limits every five seconds while the
screen is on. Nothing is read as root while the screen is off.

### What do the processor profiles do?

They set the highest speed each group of cores may run at, nothing else. **Power saving** holds
the faster cores to about 60 % of their top speed and **Balanced** to about 80 %; the slowest
group, which runs most background work cheaply, stays at full speed. **Custom** lets you choose
each group yourself, from half its top speed up. **Stock (full speed)** returns the phone's own
limits, so every core may run as fast as the phone itself allows. There is no separate
performance profile: Stock already lets the cores reach their top speed, and holding them high
all the time would only add heat, which the phone's heat protection then takes back.

Only speeds the kernel lists are used, never above what the phone allows, so nothing is
overclocked. The phone's heat protection keeps working as before: it can always hold the cores
lower still, and the page then says so.

### Why did my profile go back to Stock after a restart?

A restart returns every kernel limit to the phone's own, and by default the profile stays that
way, so a choice never outlives the moment you made it without your say. Switch on **Apply after
restart too** to have it set again the first time the screen is on after a restart.

### The page says Android raised a limit

Android's power management can write the same limit itself, for example when one of its
power hints ends and it puts its own value back. The app notices on its next five-second check and sets your limit again, and the page
counts how often. If Android keeps winning, the app stops after three tries in a row, returns the
phone's own limits and says why, rather than fight it for ever. When Android holds a group lower
than your profile (battery saver, say), the app leaves that alone and sets your limit once
Android lets go.

### Why does a group of cores say it can't be limited?

Its kernel lists no speeds for it, and the app never guesses one. Some kernels list them in a
statistics file instead; the app uses that when it is there.

### What happens when I switch root off with a profile on?

The app writes the phone's own limits back first, as the last thing root does, and then switches
root off. If root no longer answers by then, Settings says the limits stay until the phone
restarts. The same happens when Shizuku takes over from root.

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
