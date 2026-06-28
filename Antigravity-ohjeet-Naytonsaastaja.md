# Näytönsäästäjä / Always-on -näkymä — ohjeet (Google Antigravity)

Tee telakka-/näytönsäästäjänäkymä jossa **iso kello, päivämäärä ja akkutiedot**,
luettavissa kaukaa huoneesta. Toteuta **molemmat asettelut: pysty ja vaaka**
(Suunta B = minimaalinen, ei korttia). Pohjana täysmusta OLED-näkymä.

Visuaalinen referenssi: `Screensaver.dc.html` (Suunta B · pysty + Suunta B · vaaka).

---

## 1. Yhteinen tyyli (molemmat asettelut)

- **Tausta: täysmusta `#000000`** (OLED: paloja ei pala, maksimikontrasti).
- Fontti **Manrope** (otsikot/teksti). Akku-ikoni Material Symbols Rounded.
- **Kaikki numerot tabular-numeroin** (`fontFeatureSettings="tnum"` /
  `font-variant-numeric: tabular-nums`) → kello ei "hyppää" leveydessä joka minuutti.

| Elementti | Väri |
|---|---|
| Kello (tunnit:minuutit) | `#EDEDED` (himmennetty valkoinen, vähemmän hehkua) |
| Sekunnit | `#38BDF8` (syaani-aksentti) |
| Päivämäärä | `#8B929C` |
| Lataustila ("Latautuu") | `#34D399` |
| Salama-ikoni (lataus) | `#FBBF24` |
| Palkin täyttö | gradientti `#34D399 → #6EE7A8` |
| Palkin ura | `rgba(255,255,255,0.10)` |
| Akkutiedot (V / °C / täynnä) | `#9AA0A8` |

---

## 2. Koot

> `sp` tekstille, `dp` mitoille. Arvot vastaavat mockupin mittasuhteita.

### PYSTY
| Elementti | Koko / paino |
|---|---|
| Kello (tunnit:minuutit) | **120sp / 800** |
| Sekunnit | **30sp / 700** (aksenttiväri) |
| Päivämäärä | **28sp / 500** |
| Akkuprosentti | **32sp / 800** |
| Lataustila-teksti | **20sp / 700** |
| Salama / akku-ikoni | **28sp** |
| Edistymispalkki (korkeus) | **10dp** |
| Akkutiedot (V · °C / täynnä) | **18sp / 500** |

### VAAKA
| Elementti | Koko / paino |
|---|---|
| Kello (tunnit:minuutit) | **120sp / 800** |
| Sekunnit | **30sp / 700** (aksentti) |
| Päivämäärä | **30sp / 500** |
| Akkuprosentti (hero) | **58sp / 800** |
| Lataustila-teksti | **22sp / 700** |
| Salama / akku-ikoni | **30sp** |
| Edistymispalkki (korkeus) | **11dp** |
| Akkutiedot (V · °C / täynnä) | **20sp / 500**, kahdella rivillä |

---

## 3. Asettelu

### PYSTY (vertikaalisesti keskitetty pino)
```
        18:49  :27        ← kello + sekunnit (sekunnit aksenttivärillä, hieman pienempi)
   Sunnuntai 28. kesäkuuta   ← päivämäärä
        (väli ~120dp)
   ┌─────────────────────────────┐  (lähes täysleveä, ~360dp; EI korttireunaa B:ssä)
   ⚡ Latautuu              51%   ← tila vasen · prosentti oikea
   ▰▰▰▰▰▱▱▱▱▱  (palkki, koko leveys)
   3,88 V · 25,6 °C   täynnä ~20.40  ← tiedot, vasen/oikea
   └─────────────────────────────┘
```
- Ryhmä keskitetty pystysuunnassa, vaakasuunnassa keskelle.
- Tila-rivi: `space-between` (vasemmalla salama+teksti, oikealla iso %).
- Tieto-rivi: `space-between` (V·°C vasemmalla, täynnä oikealla).

### VAAKA (kaksi saraketta — koko leveys käyttöön)
```
┌───────────────────────────┬──────────────────────────────┐
│        18:49 ²⁷           │  ⚡ Latautuu                  │
│   Sunnuntai 28. kesäkuuta │  51%        ← hero-prosentti  │
│                           │  ▰▰▰▰▰▱▱▱▱  (palkki)          │
│                           │  3,88 V · 25,6 °C             │
│                           │  täynnä ~20.40                │
└───────────────────────────┴──────────────────────────────┘
   vasen ~58 %                 ohut jakoviiva   oikea ~42 %
```
- Vasen sarake: kello + päivämäärä keskitettynä.
- Ohut pystyjakoviiva `rgba(255,255,255,0.09)`, ~1.5dp.
- Oikea sarake: tila → iso prosentti → palkki → tiedot (kaksi riviä), pystysuunnassa keskitetty.
- **Android-toteutus:** käytä `res/layout/` (pysty) ja `res/layout-land/` (vaaka)
  — sama logiikka, eri XML. Vaihtoehtoisesti yksi layout joka vaihtaa suunnan
  `Configuration.ORIENTATION_LANDSCAPE` mukaan.

---

## 4. Datakentät ja lähteet

| Kenttä | Esimerkki | Lähde |
|---|---|---|
| Tunnit:minuutit | 18:49 | `Calendar` / `LocalTime`, 24h muoto |
| Sekunnit | 27 | päivitä **tick joka sekunti** (`Handler`/`Runnable`), tabular-num |
| Päivämäärä | Sunnuntai 28. kesäkuuta | `DateTimeFormatter` locale `fi`, **tarkista taivutus** |
| Akku-% | 51 | `BatteryManager.BATTERY_PROPERTY_CAPACITY` |
| Lataako | kyllä | `ACTION_BATTERY_CHANGED` → `EXTRA_STATUS` (CHARGING/FULL) |
| Jännite | 3,88 V | `EXTRA_VOLTAGE` / 1000 |
| Lämpötila | 25,6 °C | `EXTRA_TEMPERATURE` / 10 |
| Täynnä klo | ~20.40 | `BatteryManager.computeChargeTimeRemaining()` → kellonaika |

**Tilateksti & ikoni:**
- Latautuu → salama `bolt` (#FBBF24) + "Latautuu" + "täynnä ~HH.MM".
- Akkuvirralla → akku-ikoni (#34D399) + "Akkuvirralla" (täynnä-rivin tilalle voi
  laittaa esim. jäljellä-ajan tai jättää tyhjäksi).
- Täynnä → "Täynnä", palkki 100 %.

---

## 5. OLED burn-in -suojaus (pakollinen)

- **Pikselisiirto: koko sisältöä siirretään satunnaisesti enintään `±15dp`**
  (x ja y) esim. 60 s välein. Pienempi kuin aiempi ±30dp → isompikin näkymä
  pysyy turvallisesti rajojen sisällä leikkautumatta.
- **Turvamarginaalit:** jätä reunoihin vähintään ~40dp tyhjää, niin että +15dp
  siirron jälkeenkään mikään (iso kello, leveä palkki) ei mene reunan yli.
- **Himmennetyt valkoiset** (`#EDEDED`, ei `#FFFFFF`) ja ohuet/kevyet elementit
  vähentävät palavien pikseleiden määrää ja yöhehkua.
- **Ei suuria täysiä kirkkaita täyttöjä** — B-versiossa ei korttitaustaa, vain
  ohut palkki ja teksti.
- (Valinnainen) himmennä koko näkymän kirkkautta yöaikaan.

---

## 6. Valmis prompt Antigravitylle

> Rakenna Androidille telakka-/näytönsäästäjänäkymä (always-on-tyylinen) jossa
> täysmusta `#000` tausta ja iso, kaukaa luettava kello. Toteuta **sekä pysty-
> että vaaka-asettelu** (`layout/` ja `layout-land/`). Noudata yllä olevia kokoja
> (luku 2) ja värejä (luku 1). Pysty: keskitetty pino — kello `120sp` (tunnit:minuutit,
> `#EDEDED`) + sekunnit `30sp` syaani (`#38BDF8`), päivämäärä `28sp`, sitten lähes
> täysleveä akkulohko ilman korttireunaa: tila-rivi (salama + "Latautuu" vasen,
> `32sp` prosentti oikea), `10dp` paksu gradienttipalkki, ja tieto-rivi
> (`3,88 V · 25,6 °C` vasen, `täynnä ~20.40` oikea, `18sp`). Vaaka: kaksi saraketta
> — vasen kello+päivämäärä keskitettynä, ohut pystyjakoviiva, oikea akkulohko jossa
> prosentti hero-kokoisena (`58sp`) ja tiedot kahdella rivillä (`20sp`). Käytä
> **tabular-numeroita** kaikkialla. Päivitä sekunnit kerran sekunnissa. Hae akkutiedot
> luvun 4 rajapinnoista. Toteuta **OLED burn-in -suojaus**: siirrä koko sisältöä
> satunnaisesti `±15dp` ~60 s välein, jätä ~40dp turvamarginaalit, käytä himmennettyä
> valkoista `#EDEDED`. Korjaa päivämäärän taivutus oikein (esim. "kesäkuuta").
