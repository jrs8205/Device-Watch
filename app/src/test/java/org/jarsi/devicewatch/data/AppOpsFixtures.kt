package org.jarsi.devicewatch.data

/**
 * What `dumpsys appops --history --include-discrete 0` prints, in the shapes the app
 * has met. The Pixel 8a one is a real dump cut down to a few dozen rows, with the owner's
 * own apps renamed to example packages; Google's and Android's names are as printed.
 */
internal object AppOpsFixtures {

    /** Pixel 8a, Android 17; read 2026-10-13 07:12 (EEST). Pipe-separated table, newest first. */
    val PIXEL_8A_TABLE = """
AppOps Historical Service State:

History:  mode=HISTORICAL_MODE_ENABLED_ACTIVE
SHORT_INTERVAL(Aggregation Bucket)=1 Minute, LONG_INTERVAL(Aggregation Bucket)=15 Minutes
Database size(bytes): SHORT_INTERVAL=155648, LONG_INTERVAL=262144
Database Total records: SHORT_INTERVAL=1661, LONG_INTERVAL=5926
------------Discrete App Ops-------------

UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
10391|org.jarsi.devicewatch|default:0|COARSE_LOCATION|null|2026-10-13 07:12:01.990|0|2|0|fgsvc|s|-1|0
10391|org.jarsi.devicewatch|default:0|FINE_LOCATION|null|2026-10-13 07:12:01.974|0|6|0|fgsvc|s|-1|0
10294|com.google.android.gms|default:0|FINE_LOCATION|nearby_connections|2026-10-13 07:11:22.646|3|0|0|fg|s|-1|0
10186|com.google.android.apps.weather|default:0|COARSE_LOCATION|null|2026-10-13 07:09:10.155|1|0|0|bg|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|COARSE_LOCATION|google_app.notifications|2026-10-13 04:59:52.504|1|0|0|fg|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|MONITOR_LOCATION|google_app.notifications|2026-10-13 04:59:52.500|0|0|6|fg|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|MONITOR_LOCATION|google_app.notifications|2026-10-13 04:59:52.500|1|0|0|fg|s|-1|0
10186|com.google.android.apps.weather|default:0|COARSE_LOCATION|null|2026-10-13 04:27:18.936|1|0|0|cch|s|-1|0
10186|com.google.android.apps.weather|default:0|COARSE_LOCATION|null|2026-10-13 01:57:53.832|2|0|0|fg|s|-1|0
10236|com.google.android.marvin.talkback|default:0|BIND_ACCESSIBILITY_SERVICE|null|2026-10-13 00:59:27.442|4|0|0|cch|s|-1|0
1000|android|default:0|MONITOR_LOCATION|GnssService|2026-10-12 21:54:30.974|0|0|825|pers|s|-1|0
1000|android|default:0|MONITOR_LOCATION|GnssService|2026-10-12 21:54:30.974|1|0|0|pers|s|-1|0
10389|com.example.navigator|default:0|GPS|null|2026-10-12 21:54:30.792|0|0|14|top|s|-1|0
10389|com.example.navigator|default:0|GPS|null|2026-10-12 21:54:30.792|1|0|0|top|s|-1|0
10389|com.example.navigator|default:0|FINE_LOCATION|null|2026-10-12 21:54:30.777|1|0|0|top|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_HIGH_POWER_LOCATION|gnss_location_provider|2026-10-12 21:54:30.777|0|0|27|fg|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_HIGH_POWER_LOCATION|gnss_location_provider|2026-10-12 21:54:30.777|1|0|0|fg|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|gnss_location_provider|2026-10-12 21:54:30.776|0|0|28|fg|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|gnss_location_provider|2026-10-12 21:54:30.776|1|0|0|fg|s|-1|0
10389|com.example.navigator|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 21:54:30.771|0|0|32|top|s|-1|0
10389|com.example.navigator|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 21:54:30.771|1|0|0|top|s|-1|0
10389|com.example.navigator|default:0|MONITOR_LOCATION|null|2026-10-12 21:54:30.769|0|0|34|top|s|-1|0
10389|com.example.navigator|default:0|MONITOR_LOCATION|null|2026-10-12 21:54:30.769|2|0|0|top|s|-1|0
10391|org.jarsi.devicewatch|default:0|COARSE_LOCATION|null|2026-10-12 21:54:00.077|10|0|0|top|s|-1|0
10391|org.jarsi.devicewatch|default:0|FINE_LOCATION|null|2026-10-12 21:54:00.071|32|0|0|top|s|-1|0
10236|com.google.android.marvin.talkback|default:0|BIND_ACCESSIBILITY_SERVICE|null|2026-10-12 21:53:01.363|6|0|0|bg|s|-1|0
10236|com.google.android.marvin.talkback|default:0|BIND_ACCESSIBILITY_SERVICE|null|2026-10-12 21:52:59.959|4|0|0|fg|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-12 21:51:04.173|0|2|0|bg|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|current_semantic_location|2026-10-12 21:03:53.980|0|0|51|fgsvc|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|current_semantic_location|2026-10-12 21:03:53.980|2|0|0|fgsvc|s|-1|0
1000|android|default:0|FINE_LOCATION|CountryDetector|2026-10-12 21:00:22.197|1|0|0|pers|s|-1|0
10383|com.example.camera.debug|default:0|CAMERA|null|2026-10-12 20:57:39.779|0|0|80947|top|s|-1|0
10383|com.example.camera.debug|default:0|CAMERA|null|2026-10-12 20:57:39.779|1|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:57:38.721|0|0|81997|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:57:38.721|1|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|FINE_LOCATION|null|2026-10-12 20:57:38.720|2|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|MONITOR_LOCATION|null|2026-10-12 20:57:38.719|0|0|81986|top|s|-1|0
10383|com.example.camera.debug|default:0|MONITOR_LOCATION|null|2026-10-12 20:57:38.719|2|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 20:57:38.719|0|0|81926|top|s|-1|0
10383|com.example.camera.debug|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 20:57:38.719|1|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:56:34.441|0|0|9713|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:56:34.441|2|0|0|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:49:53.550|0|0|9708|top|s|-1|0
10383|com.example.camera.debug|default:0|GPS|null|2026-10-12 20:49:53.550|1|0|0|top|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-12 20:49:30.869|0|2|0|bg|s|-1|0
10186|com.google.android.apps.weather|default:0|MONITOR_LOCATION|null|2026-10-12 20:49:19.727|0|0|1016|bg|s|-1|0
10186|com.google.android.apps.weather|default:0|MONITOR_LOCATION|null|2026-10-12 20:49:19.727|1|0|0|bg|s|-1|0
1000|android|default:0|COARSE_LOCATION|null|2026-10-12 20:49:03.103|2|0|0|pers|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|COARSE_LOCATION|google_app.search_listener|2026-10-12 20:11:19.945|1|0|0|top|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|MONITOR_LOCATION|google_app.search_listener|2026-10-12 20:11:19.943|0|0|2684|top|s|-1|0
10182|com.google.android.googlequicksearchbox|default:0|MONITOR_LOCATION|google_app.search_listener|2026-10-12 20:11:19.943|1|0|0|top|s|-1|0
1000|com.android.inputdevices|default:0|FINE_LOCATION|null|2026-10-12 20:11:03.555|1|0|0|pers|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|current_semantic_location|2026-10-12 17:03:57.737|0|0|1|cch|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|current_semantic_location|2026-10-12 17:03:57.737|1|0|0|cch|s|-1|0
10171|com.google.android.GoogleCamera|default:0|GPS|null|2026-10-12 09:36:03.513|0|0|1653|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|GPS|null|2026-10-12 09:36:03.513|1|0|0|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 09:36:03.495|0|0|1637|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|MONITOR_HIGH_POWER_LOCATION|null|2026-10-12 09:36:03.495|1|0|0|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|MONITOR_LOCATION|null|2026-10-12 09:36:03.494|0|0|1721|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|MONITOR_LOCATION|null|2026-10-12 09:36:03.494|1|0|0|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|FINE_LOCATION|null|2026-10-12 09:36:03.485|1|0|0|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|CAMERA|null|2026-10-12 09:36:03.307|0|0|1863|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|CAMERA|null|2026-10-12 09:36:03.307|1|0|0|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|GPS|null|2026-10-12 09:23:17.803|0|0|2235|top|s|-1|0
10171|com.google.android.GoogleCamera|default:0|GPS|null|2026-10-12 09:23:17.803|1|0|0|top|s|-1|0
10177|com.google.android.setupwizard|default:0|FINE_LOCATION|null|2026-10-12 07:06:37.394|2|0|0|top|s|-1|0
10294|com.google.android.gms|default:0|FINE_LOCATION|nearby_connections|2026-10-12 07:06:33.197|1|0|0|fgsvc|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-12 01:16:45.944|0|2|0|fg|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-11 22:10:12.810|0|2|0|bg|s|-1|0
10294|com.google.android.gms|default:0|FINE_LOCATION|nearby_connections|2026-10-11 22:09:30.645|2|0|0|top|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|com.google.android.gms.personalsafety|2026-10-11 22:09:08.448|0|0|8742|top|s|-1|0
10294|com.google.android.gms|default:0|MONITOR_LOCATION|com.google.android.gms.personalsafety|2026-10-11 22:09:08.448|3|0|0|top|s|-1|0
10177|com.google.android.setupwizard|default:0|FINE_LOCATION|null|2026-10-11 21:44:51.881|1|0|0|fgsvc|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-11 21:39:05.930|0|2|0|bg|s|-1|0
10177|com.google.android.setupwizard|default:0|FINE_LOCATION|null|2026-10-11 21:38:42.685|2|0|0|bg|s|-1|0
10177|com.google.android.setupwizard|default:0|FINE_LOCATION|null|2026-10-11 21:27:13.403|7|0|0|fg|s|-1|0
10238|com.google.android.contacts|default:0|READ_DEVICE_IDENTIFIERS|null|2026-10-11 21:26:03.666|0|2|0|fg|s|-1|0
""".trimIndent()

    /** Android 14 emulator (API 34); read 2026-10-13. The older indented layout: one access per line, durations quantized to the minute. */
    val EMULATOR_API34_TREE = """
  User restrictions for token android.os.Binder@19403e0:
      Restricted ops:
        user: 0 restricted ops: [PHONE_CALL_MICROPHONE, PHONE_CALL_CAMERA]
    Excluded packages:
      user: 0 packages:
        com.android.server.telecom[*]
Discrete accesses:
  Largest chain id: 0
  Uid: 10128
    Package: com.google.android.gms
      FINE_LOCATION
        Attribution: network_location_provider
          Access [fg-s] at 2026-10-11 10:37:00.000
        Attribution: location_nlp_network
          Access [fg-s] at 2026-10-11 10:37:00.000
          Access [fg-s] at 2026-10-11 12:38:00.000
          Access [fg-s] at 2026-10-13 04:51:00.000
        Attribution: com.google.android.gms.gcm
          Access [fg-s] at 2026-10-11 10:37:00.000
          Access [fg-s] at 2026-10-11 10:39:00.000
          Access [fg-s] at 2026-10-13 04:51:00.000
        Attribution: fused_location_provider
          Access [fg-s] at 2026-10-11 10:37:00.000
          Access [fg-s] at 2026-10-13 04:51:00.000
          Access [fg-s] at 2026-10-13 04:51:00.000
        Attribution: geofencer_provider
          Access [fg-s] at 2026-10-11 10:37:00.000
          Access [fg-s] at 2026-10-13 04:51:00.000
        Attribution: location_accuracy
          Access [fg-s] at 2026-10-11 10:41:00.000
  Uid: 10144
    Package: com.android.camera2
      CAMERA
        Attribution: null
          Access [top-s] at 2026-10-13 05:01:00.000 attribution flags=1 with chainId=-1
          Access [top-s] at 2026-10-13 05:01:00.000 for 60000 milliseconds  attribution flags=1 with chainId=-1
      RECORD_AUDIO
        Attribution: null
          Access [top-s] at 2026-10-13 05:01:00.000 attribution flags=1 with chainId=-1
          Access [top-s] at 2026-10-13 05:01:00.000 for 60000 milliseconds  attribution flags=1 with chainId=-1
""".trimIndent()

    /**
     * Android 16 emulator (API 36, `google_apis`); read 2026-10-13 15:33 (the emulator's clock is UTC).
     * Still the tree, with a `Device:` level between the operation and its attributions, times to the
     * millisecond and lengths as measured. The camera was refused once while this was read (its op
     * set to ignore, "Can't connect to the camera" on screen) and no line of it was written.
     */
    val EMULATOR_API36_TREE = """
        Uid u0a212:
          Package com.google.android.cellbroadcastreceiver:
            Attribution null:
              USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER:
                [bg-s] = reject=9
  Empty
Discrete accesses:
  Largest chain id: 0
  Uid: 1000
    Package: android
      COARSE_LOCATION
        Device: default:0
          Attribution: null
            Access [pers-s] at 2026-10-13 15:26:38.143
            Access [pers-s] at 2026-10-13 15:26:53.318
      FINE_LOCATION
        Device: default:0
          Attribution: null
            Access [pers-s] at 2026-10-13 15:30:50.262
          Attribution: SensorNotificationService
            Access [pers-s] at 2026-10-13 15:30:50.242
  Uid: 10145
    Package: com.google.android.gms
      FINE_LOCATION
        Device: default:0
          Attribution: network_location_provider
            Access [fg-s] at 2026-10-13 15:26:59.583
          Attribution: com.google.android.gms.personalsafety
            Access [fg-s] at 2026-10-13 15:30:50.372
          Attribution: com.google.android.gms.gcm
            Access [fg-s] at 2026-10-13 15:27:20.839
          Attribution: nearby_connections
            Access [fg-s] at 2026-10-13 15:27:09.266
  Uid: 10154
    Package: com.android.camera2
      FINE_LOCATION
        Device: default:0
          Attribution: null
            Access [top-s] at 2026-10-13 15:30:50.180
            Access [top-s] at 2026-10-13 15:31:01.688
      CAMERA
        Device: default:0
          Attribution: null
            Access [top-s] at 2026-10-13 15:30:47.442
            Access [top-s] at 2026-10-13 15:30:47.442 for 15290 milliseconds
            Access [top-s] at 2026-10-13 15:31:26.349
            Access [top-s] at 2026-10-13 15:31:26.349 for 6224 milliseconds
""".trimIndent()
}
