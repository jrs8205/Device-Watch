# System Monitor Widget — rakennusohjeet (Google Antigravity)

Tämä dokumentti kuvaa täsmälleen miltä widgetin tulee näyttää ja toimia, jotta sen voi
rakentaa pikselintarkasti. Tyyli vastaa "Tyyli 2 · Ruudukko" -versiota. **Sekä tumma
että vaalea teema on toteutettava** ja niiden tulee seurata järjestelmän teemaa.

---

## 1. Tavoite ja kohdealusta

- **Mikä:** Androidin kotinäytön widget, joka näyttää laitteen tilan tiiviinä tietoruudukkona.
- **Suositeltu toteutus:** **Jetpack Glance** (`androidx.glance:glance-appwidget`) + Compose-tyylinen
  rakentaminen. Glance riittää, koska tämä tyyli käyttää vain **lineaarisia palkkeja** (ei
  ympyrämittareita eikä SVG:tä), jotka Glance osaa natiivisti.
- **Vaihtoehto:** tavallinen `AppWidgetProvider` + `RemoteViews`, tai Flutter
  `home_widget`. Designtokenit alla ovat alustariippumattomia.
- **Päivitys:** `updatePeriodMillis` taustapäivitykseen + manuaalinen "Päivitä tiedot"
  -painike, joka käynnistää `GlanceAppWidget.update()`/`onUpdate()`.

---

## 2. Designjärjestelmä (tokenit)

### Typografia
> **Päivitetty mitoitus (isommat tekstit ja kosketusalueet).** Käytä alla olevia
> kokoja. Androidilla käytä **sp** tekstille ja **dp** mitoille.

| Rooli | Fontti | Koko / paino |
|---|---|---|
| Otsikko ("Järjestelmämonitori") | Manrope | 21px / 800 |
| Laatan arvo (iso luku, esim. "64%") | Manrope | 32px / 800, "%" 17px |
| Laatan otsikko (AKKU, MUISTI…) | Manrope | 12px / 700, UPPERCASE, letter-spacing 0.6px |
| Sirun (tila-painike) teksti | Manrope | 15px / 600 |
| Verkko-laatan SSID / Akun terveys | Manrope | 18px / 700 · 22px / 800 |
| Numero-/datatekstit (GB, dBm, Mb/s) | JetBrains Mono | 13–15px / 500–700 |
| Footer (uptime / päivitetty) | JetBrains Mono | 14px / 500 |
| Painikkeen teksti | Manrope | 18px / 700 |
| Ikonit | Material Symbols Rounded (FILL 1) | 18–25px |

Lataa fontit: Manrope, JetBrains Mono, Material Symbols Rounded (Google Fonts).
Androidilla bundlaa fontit `res/font/` -kansioon; Material Symbols voi korvata
`androidx` vektoreilla (`ic_*`) jos ligatuurifonttia ei haluta.

### Mitat
- Ulkokortin pyöristys: **30px**, sisäpadding **24px**.
- Laatta: pyöristys **20px**, padding **17px**.
- Ruudukko: 2 saraketta, väli (gap) **12px**.
- Palkki (progress): korkeus **8px**, pyöristys täysi (99px).
- Painike: pyöristys **18px**, padding **17px**, ikoni 22px.

### Tila-painikkeet (status chips) — kosketettavat
- **Asettelu: 3 saraketta × 2 riviä** (`grid`, 3×`1fr`, gap **10px**) — ei kääriytyvä rivi,
  jotta painikkeet ovat tasakokoiset ja isot.
- Jokainen siru: keskitetty ikoni + teksti (`gap 7px`), padding **14×10px**,
  pyöristys **14px**, ikoni **20px**, teksti **14px / 600**.
- **Älä anna sirujen valua reunan yli.** Pidä sarakkeet tasaleveinä ja yksirivisinä:
  - Androidilla käytä vaakasuoraa `LinearLayout`ia jossa **jokaisella sirulla
    `layout_width=0dp` + `layout_weight=1`** (älä kiinteät leveydet, ei vaakavieritystä).
  - Teksti **yhdellä rivillä**: `maxLines=1`, `ellipsize=end` tai `autoSizeTextType`.
    CSS-mockissa: `min-width:0; white-space:nowrap`.
  - **Käytä lyhyitä nimiä** jotteivät levät sanat pakota sarakkeita yli widgetin:
    **DND, Bluetooth, Sijainti, NFC, Säästö, Lentotila** (pisin sana määrää koon).
- **Kosketusalue vähintään 48 dp korkea** (padding 14 + tekstirivi ≈ 22 → ~50 dp).
  Anna jokaiselle sirulle oma `clickable`/`PendingIntent`, joka avaa oikean
  järjestelmäasetuksen (ks. taulukko alla). Koko siru on klikattava, ei vain teksti.
- Tilan osoitus: **päällä** = aksenttiväri (tausta `rgba(aksentti,0.14)`, reuna
  `rgba(aksentti,0.30)`, ikoni+teksti kirkas); **pois** = himmeä (harmaa ikoni/teksti,
  tausta `rgba(255,255,255,0.03)` / vaaleassa `rgba(0,0,0,0.04)`).

| Tila-painike (lyhyt nimi) | Avaa (Intent / Action) |
|---|---|
| DND (Älä häiritse) | `Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS` tai `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |
| Bluetooth | `Settings.ACTION_BLUETOOTH_SETTINGS` |
| Sijainti | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` |
| NFC | `Settings.ACTION_NFC_SETTINGS` |
| Säästö (Virransäästö) | `Settings.ACTION_BATTERY_SAVER_SETTINGS` |
| Lentotila | `Settings.ACTION_AIRPLANE_MODE_SETTINGS` |

### Värit — TUMMA teema (oletus)
| Token | Arvo |
|---|---|
| Kortin tausta | `rgba(18,21,26,0.86)` (blur taustalla) |
| Kortin reuna | `rgba(255,255,255,0.07)` |
| Laatan tausta | `rgba(255,255,255,0.04)` |
| Laatan reuna | `rgba(255,255,255,0.05)` |
| Palkin ura | `rgba(255,255,255,0.08)` |
| Teksti ensisijainen | `#FFFFFF` / `#F2F4F7` |
| Teksti toissijainen | `#9AA0A8` |
| Teksti vaimennettu / mono | `#7B828C` |

### Värit — VAALEA teema (pakollinen)
| Token | Arvo |
|---|---|
| Kortin tausta | `rgba(255,255,255,0.94)` |
| Kortin reuna | `rgba(0,0,0,0.07)` |
| Laatan tausta | `rgba(0,0,0,0.035)` |
| Laatan reuna | `rgba(0,0,0,0.06)` |
| Palkin ura | `rgba(0,0,0,0.09)` |
| Teksti ensisijainen | `#1A1D21` |
| Teksti toissijainen | `#6B7280` |
| Teksti vaimennettu / mono | `#8A909A` |

### Aksenttivärit (gradientit palkeissa, ikoneissa)
Tummassa teemassa käytä kirkkaita sävyjä; **vaaleassa syvennä** riittävän kontrastin
vuoksi. Palkit ovat lineaarisia gradientteja vasemmalta oikealle.

| Metriikka | Tumma (alku → loppu) | Vaalea (alku → loppu) | Ikoni |
|---|---|---|---|
| Akku | `#34D399 → #A3E635` | `#34D399 → #84CC16` | `battery_horiz_075` |
| Muisti (RAM) | `#F59E0B → #FB923C` | sama | `memory` |
| Suoritin (CPU) | `#2DD4BF → #22D3EE` | sama | `developer_board` |
| Tallennus | `#8B5CF6 → #A78BFA` | sama | `sd_card` |
| Akun terveys | `#34D399 → #6EE7A8` | `#34D399 → #34D399` | `favorite` |
| Verkko / Bluetooth | `#38BDF8` | sama | `wifi` / `bluetooth` |
| Sijainti | `#34D399` | `#16A34A` | `location_on` |
| NFC | `#2DD4BF` | sama | `nfc` |
| Älä häiritse | `#A78BFA` | `#7C3AED` | `do_not_disturb_on` |
| "live"-merkki / bolt | `#34D399` / `#FBBF24` | `#15803D` / `#D97706` | `bolt` |

---

## 3. Layout-rakenne (ylhäältä alas)

```
Kortti (pyöristetty, teeman tausta)
├─ Header-rivi
│   ├─ vasen: ⚡bolt-ikoni + "Järjestelmämonitori"
│   └─ oikea: "live"-pilleri (vihreä piste + teksti)
├─ Ruudukko 2×3 (6 metriikkalaattaa)
│   1. AKKU      4. TALLENNUS
│   2. MUISTI    5. VERKKO (Wi-Fi)
│   3. SUORITIN  6. AKUN TERVEYS
├─ Yhteydet & tilat -laatta (täysleveä)
│   ├─ Mobiiliverkko-rivi (operaattori+5G, dBm, mobiilidata X/Y GB)
│   ├─ ohut jakoviiva
│   └─ tilasirut (3 saraketta × 2 riviä, isot kosketusalueet): päällä värillisinä, pois himmeinä
├─ Footer-rivi: "Uptime 39t 34m"  ·  "Päivitetty 14.07"
└─ Painike "Päivitä tiedot" (gradientti, ikoni refresh)
```

### Metriikkalaatan anatomia (toistuu 6×)
```
┌──────────────────────────┐
│ OTSIKKO(uppercase)   [ikoni]│
│ 64%   ← iso arvo            │
│ aliteksti (mono)           │
│ ▰▰▰▰▱▱▱  ← gradienttipalkki │
│ lisätieto (mono)           │
└──────────────────────────┘
```
Poikkeukset:
- **Verkko-laatta:** ei palkkia. Sisältö: SSID iso, `south`-ikoni + alanopeus,
  `north`-ikoni + ylänopeus, "X GB tänään".
- **Akun terveys -laatta:** "Hyvä" (vihreä), "96 % kapasiteetti", palkki 96%,
  "312 lataussykliä".

### Tilasiru (status chip)
- **Aktiivinen (päällä):** tausta `rgba(aksentti, 0.14)`, reuna `rgba(aksentti, 0.28)`,
  ikoni aksenttivärillä, teksti ensisijainen.
- **Ei-aktiivinen (pois):** tausta `rgba(255,255,255,0.03)` (tumma) /
  `rgba(0,0,0,0.04)` (vaalea), reuna laatan reuna, ikoni+teksti vaimennettu harmaa.

---

## 4. Datakentät ja Android-lähteet

| Kenttä | Esimerkki | Android-lähde |
|---|---|---|
| Akku % | 64 | `BatteryManager.BATTERY_PROPERTY_CAPACITY` |
| Aika jäljellä | ~11t 53m | purku: arvio virrankulutuksesta; lataus: `computeChargeTimeRemaining()` |
| Akun lämpö | 29,5°C | `ACTION_BATTERY_CHANGED` → `EXTRA_TEMPERATURE` / 10 |
| Jännite | 4,01V | `EXTRA_VOLTAGE` / 1000 |
| Akun kunto | Hyvä | `EXTRA_HEALTH` (GOOD/OVERHEAT/…) |
| Kapasiteetti % | 96 | terveysarvio; tarkka vaatii valmistajan rajapintaa |
| Lataussyklit | 312 | `BATTERY_PROPERTY_CYCLE_COUNT` (API 34+) |
| RAM käytetty/koko | 5,7 / 7,4 GB | `ActivityManager.MemoryInfo` (`totalMem`−`availMem`) |
| CPU-ytimet | 9 | `Runtime.getRuntime().availableProcessors()` |
| CPU-kello | 2,1 GHz | `/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq` |
| CPU-kuorma % | 23 | `/proc/stat` delta (rajattu uusissa Androideissa — käytä arviota) |
| CPU-lämpö | 42°C | `/sys/class/thermal/thermal_zone*/temp` (vaihtelee laitteittain) |
| Tallennustila | 89 / 128 GB | `StatFs` tai `StorageStatsManager` |
| Wi-Fi SSID / nopeus | Koti_5G, 48/12 Mb/s | `ConnectivityManager` + `NetworkCapabilities.linkDownstreamBandwidthKbps` |
| Mobiiliverkko | DNA · 5G, −87 dBm | `TelephonyManager` + `SignalStrength` |
| Datankäyttö | 4,2 / 20 GB | `NetworkStatsManager` (vaatii `PACKAGE_USAGE_STATS`) |
| Uptime | 39t 34m | `SystemClock.elapsedRealtime()` |
| Älä häiritse | päällä | `NotificationManager.getCurrentInterruptionFilter()` |
| Bluetooth | päällä | `BluetoothAdapter.isEnabled()` |
| Sijainti | päällä | `LocationManager.isLocationEnabled()` |
| NFC | päällä | `NfcAdapter.isEnabled()` |
| Virransäästö | pois | `PowerManager.isPowerSaveMode()` |
| Lentokonetila | pois | `Settings.Global.AIRPLANE_MODE_ON` |

**Tarvittavat luvat (Manifest):** `READ_PHONE_STATE` (signaali),
`PACKAGE_USAGE_STATS` (datankäyttö, erikoislupa asetuksista),
`BLUETOOTH_CONNECT`, `ACCESS_NETWORK_STATE`. Pyydä vain mitä käytät; jos lupaa ei
ole, näytä kenttä muodossa "—".

---

## 5. Tilalogiikka (väri tilan mukaan)

Värin valinta arvon mukaan (kynnykset):
- **Akku:** ≥50 % vihreä, 20–49 % keltainen (`#FBBF24`), <20 % punainen (`#F87171`).
- **RAM / CPU / Tallennus:** ≤60 % oma aksentti, 61–85 % keltainen, >85 % punainen.
- **Akun kunto:** GOOD → vihreä "Hyvä", muut → keltainen/punainen vastaava teksti.

Vaihda sekä palkin gradientti että mahdollinen otsikon korostus tämän mukaan.

---

## 6. Teeman vaihto (dark/light)

- Käytä järjestelmän teemaa: Glancessa `GlanceTheme` / DayNight-resurssit, tai
  `resources.configuration.uiMode` & `Configuration.UI_MODE_NIGHT_*`.
- Määrittele molemmat tokensetit (luku 2) ja valitse aktiivinen teeman mukaan.
- Aksentit pysyvät samoina paitsi taulukon "Vaalea"-sarakkeen syvennetyt sävyt.
- Testaa molemmat teemat sekä vaalealla että tummalla taustakuvalla.

---

## 7. Esimerkkidata (käytä mockissa)

```
Akku 64 % · 11t 53m jäljellä · 29,5°C · 4,01V
Muisti 76 % · 5,7 / 7,4 GB · 1,8 GB vapaana
Suoritin 23 % · 9 ydintä · 2,1 GHz · 42°C
Tallennus 70 % · 89 / 128 GB · 39 GB vapaana
Verkko Wi-Fi Koti_5G · ↓48 ↑12 Mb/s · 1,8 GB tänään
Akun terveys Hyvä · 96 % · 312 lataussykliä
Mobiili DNA · 5G · −87 dBm · 4,2 / 20 GB
Tilat: Älä häiritse=on, Bluetooth=on, Sijainti=on, NFC=on,
       Virransäästö=off, Lentokonetila=off
Uptime 39t 34m · Päivitetty 14.07
```

---

## 8. Valmis prompt Antigravitylle (liitä sellaisenaan)

> Rakenna Android-kotinäytön widget Jetpack Glancella nimellä "System Monitor".
> Se näyttää laitteen tilan tiiviinä tietoruudukkona. Noudata oheista
> designspecistä (luvut 2–7): pyöristetty kortti, header jossa ⚡-ikoni +
> "Järjestelmämonitori" ja vihreä "live"-pilleri, 2×3 metriikkalaattaruudukko
> (Akku, Muisti, Suoritin, Tallennus, Verkko, Akun terveys), kullakin oma
> aksenttiväri, iso luku ja 6px lineaarinen gradienttipalkki. Sen alle täysleveä
> "Yhteydet & tilat" -laatta: mobiiliverkkorivi (operaattori+5G, dBm,
> mobiilidata X/Y GB) ja **3×2-ruudukko tilasiruja** (Älä häiritse, Bluetooth,
> Sijainti, NFC, Virransäästö, Lentokonetila) — päällä olevat aksenttivärillä,
> pois olevat himmeinä; **jokainen siru on klikattava** ja avaa oikean asetuksen,
> kosketusalue ≥ 48 dp. Footerissa uptime + päivitysaika, alimpana gradientti­-
> painike "Päivitä tiedot". Käytä Manrope-, JetBrains Mono- ja Material Symbols
> Rounded -fontteja. Toteuta SEKÄ tumma että vaalea teema annetuin tokenein ja
> seuraa järjestelmän teemaa. Hae oikeat arvot luvun 4 rajapinnoista; käytä
> luvun 7 esimerkkidataa kunnes rajapinnat on kytketty. Väritä palkit luvun 5
> kynnyksin. Lisää manuaalinen päivitys painikkeesta ja taustapäivitys.

---

*Visuaalinen referenssi: ks. `System Monitor Widget.dc.html` (Tyyli 2 tumma +
"Tyyli 2 · Vaalea teema").*
