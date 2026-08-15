# Handoff: Device Watch / Laitevahti — UI-suunta 1b "Mittaristo" (tumma + vaalea, WCAG AAA)

## Yleiskuvaus
Tämä paketti kuvaa uuden yleisen tyylin Device Watch -sovelluksen dashboardille: kortit korvataan
hiusviivoin jaetuilla kaistoilla, numerot latautuvat monospace-fontille, ja koko paletti on laskettu
WCAG 2.2 AAA -tasolle. Suunta on tehty niin, että se kestää Androidin **suurimman fonttikoon (2.0×)**
ilman leikkautumista: jokainen arvo on otsikkonsa alla omalla rivillään.

Lähtökohta on repon haara **v1.6.0 (commit 162bd9f)** — erityisesti `LabelValueRow`,
`SettingsToggleRow`, akkukortin `FlowRow` ja `clampedNavBarFontScale = 1.3`.
**Mainiin ei kosketa.** Tämä paketti on tarkoitettu omalle haaralle (ks. "Haaraan vieminen").

## Tiedostoista
Kansiossa `design/` olevat tiedostot ovat **design-referenssejä, jotka on tehty HTML:llä** — ne
näyttävät tavoitellun ilmeen ja mittasuhteet, eivät ole tuotantokoodia. Tehtävä on **toteuttaa nämä
näkymät sovelluksen omassa ympäristössä**, eli Jetpack Composella Material 3:n päälle, käyttäen repon
olemassa olevia rakenteita (`SettingsSectionCard`/kaistat, `LabelValueRow`, `DashboardTabs`,
`ui/theme/Color.kt`, `Theme.kt`). HTML:ää ei käännetä eikä kopioida sellaisenaan.

Avaa HTML-tiedostot selaimessa suoraan (`design/support.js` on niiden ajonaikainen apuri, sen pitää
olla samassa kansiossa).

## Tarkkuus
**Hi-fi.** Värit, kokoluokat, välit ja tekstit ovat lopullisia ehdotuksia ja ne on tarkoitettu
toteutettavaksi pikselintarkasti — pl. esimerkkiluvut (78 %, 4t 12m, jne.), jotka ovat mockup-dataa.

## Näkymät
### 1. Etusivu (OverviewTab) — tumma teema, `2a`
Sama tietosisältö kuin nykyisessä OverviewTabissa, uudella rakenteella:

| Kaista | Sisältö |
| --- | --- |
| Yläpalkki | Ylärivi "LAITEVAHTI" 12 sp, letter-spacing 0.14em, vaimennettu; sen alla "Järjestelmän tila" 20 sp bold. Oikealla päivitysikoni 24 dp, kosketuskohde 48×48 dp. Alla 1 dp rakenneviiva. |
| AKKU | Osion otsikko 12 sp / 0.14em. Oikealla tilamerkki: 10 dp pallo + teksti (KÄYTTÖ / LATAUTUU / TÄYNNÄ). Iso arvo mono 44 sp / weight 500, "%"-yksikkö 45 % koosta vaimennettuna. Alla 12 dp korkea palkki (radius 2 dp). Sen alla kolme label/arvo-paria (JÄLJELLÄ, LÄMPÖ, JÄNNITE), FlowRow-tyyliin rivittyen: otsikko 12 sp, arvo mono 18 sp. |
| KÄYTTÖ TÄNÄÄN | Otsikko + oikealla "Historia ›" (13 sp + 20 dp chevron), koko kaista avaa HistoryPagen. Rivit: Ruutuaika, Lukituksen avaukset, Ilmoitukset, Käynnistykset, Latauskerrat — jokainen **otsikko 13 sp vaimennettu ylärivillä, arvo mono 22 sp / 500 alarivillä**. Lopussa jaksovertailu: "Ruutuaika vs. eilen" → "4t 48m → 4t 12m" mono 18 sp + muutos-% aksenttivärillä. |
| DATA · KIINTIÖ 20 GB | Mobiili (SIM-nimi suluissa): arvo "4.8 / 20 GB" mono 22 sp + 8 dp kiintiöpalkki. Wi-Fi: pelkkä arvo. Ilman kiintiötä otsikko on "DATA" ja palkki jätetään pois. |
| RESURSSIT | Kolme mittaria (Käyttömuisti, Suoritin · N ydintä, Tallennustila · X GB vapaana): otsikko 13 sp, arvo mono 22 sp, 8 dp palkki. Lopussa "Käyntiaika · päivitetty" → "42t 18m · 15.44" mono 18 sp. |
| Alanavigointi | 76 dp korkea, 1 dp yläviiva. Neljä kohtaa: ikoni 24 dp + label 12 sp. Valittu kohta: aksenttiväri **ja** 3 dp yläviiva. Fonttiskaala katkaistaan 1.3×:ään (`clampedNavBarFontScale`), label 1 rivi + ellipsis. |

### 2. Etusivu — vaalea teema, `2b`
Identtinen rakenne, oma paletti (alla). Vaalea ja tumma valitaan `isSystemInDarkTheme()`:n mukaan.

### 3. Suurin fonttikoko (2.0×)
Molemmista teemoista on paketissa versio, jossa kaikki tekstikoot on kerrottu kahdella ja
dp-mitat (ikonit, palkit, välit, kulmat, navipalkki) pidetty ennallaan — juuri niin kuin Android
tekee. Mikään rivi ei leikkaudu, koska yksikään arvo ei kilpaile leveydestä otsikkonsa kanssa.

## Design-tokenit
### Tumma (pohja `#0A0A0C`)
| Token | Hex | Käyttö | Kontrasti | Vaatimus |
| --- | --- | --- | --- | --- |
| background | `#0A0A0C` | sivun pohja | — | — |
| onSurface | `#F2F2F5` | leipäteksti, mono-arvot | 17.7:1 | AAA 7:1 |
| onSurfaceVariant | `#A2A2AE` | otsikot 12–13 sp | 7.82:1 | AAA 7:1 |
| accentText | `#90CAF9` | linkit, "Historia", ikonit, muutos-% | 11.3:1 | AAA 7:1 |
| accentGraphic | `#2196F3` | mittarien täyttö, navi-indikaattori | 6.33:1 (pohja) / 4.55:1 (ura) | 1.4.11 3:1 |
| statusOk | `#66BB6A` | tilateksti + pallo | 8.42:1 | AAA 7:1 |
| outline | `#6A6A75` | kaistojen 1 dp rakenneviivat | 3.70:1 | 1.4.11 3:1 |
| track | `#2A2A32` | mittarin ura | 1.39:1 (koriste) | — |

### Vaalea (pohja `#FAFAFC`)
| Token | Hex | Käyttö | Kontrasti | Vaatimus |
| --- | --- | --- | --- | --- |
| background | `#FAFAFC` | sivun pohja | — | — |
| onSurface | `#101014` | leipäteksti, mono-arvot | 18.2:1 | AAA 7:1 |
| onSurfaceVariant | `#4E4E58` | otsikot 12–13 sp | 7.88:1 | AAA 7:1 |
| accent | `#0A4C87` | aksenttiteksti, ikonit, mittarit, navi-indikaattori | 8.41:1 | AAA 7:1 |
| statusOk | `#145A1E` | tilateksti + pallo | 8.03:1 | AAA 7:1 |
| outline | `#8E8E99` | kaistojen 1 dp rakenneviivat | 3.11:1 | 1.4.11 3:1 |
| track | `#DADAE2` | mittarin ura (täyttö vs. ura 6.31:1) | 1.33:1 (koriste) | — |

Huom: `#2196F3` (Color.kt `WaterBlue`) ei kelpaa vaalealla tekstivärinä (~3:1), siksi vaalean
aksentti on tummennettu `#0A4C87`. Tumman teeman aksentti on jaettu tekstiin (`#90CAF9`) ja
grafiikkaan (`#2196F3`), koska kirkas sininen ei yksin täytä 7:1:tä pienessä tekstissä.

### Typografia
- Otsikot ja leipäteksti: **Roboto** (Compose-oletus).
- Kaikki numerot ja mittariarvot: **Roboto Mono** (`FontFamily.Monospace` tai `androidx.compose.ui.text.font` -resurssifontti) — tasalevyiset numerot pitävät mittarit paikallaan kun arvot päivittyvät.
- Skaala (sp): 12 osion otsikot / 13 rivin otsikot / 18 sekundääriarvot mono / 20 sivun otsikko bold / 22 pääarvot mono 500 / 44 akkuprosentti mono 500. Alle 12 sp ei käytetä missään.

### Mitat (dp, eivät skaalaudu fontin mukana)
Kaistan sisennys 20, kaistan pystyväli 18, rakenneviiva 1, akkupalkki 12 (radius 2), muut palkit 8
(radius 2), ikoni 24, kosketuskohde 48, navipalkki 76, navin aktiivinen yläviiva 3, tilapallo 10.

## Käyttäytyminen
- Kaistat "KÄYTTÖ TÄNÄÄN" ja "AKKU" ovat kokonaan klikattavia (HistoryPage / SinceChargePage), sama `clickable(onClick = withTapHaptic(...))` kuin nyt.
- Pull-to-refresh ja `HorizontalPager`-välilehtien pyyhkäisy jäävät ennalleen.
- Väri ei koskaan ole ainoa tiedon välittäjä (1.4.1): tilapallon rinnalla on aina tekstitila, ja navin valinta merkitään myös 3 dp viivalla.
- Fonttiskaalaus: sisältönäkymät saavat koko systeemiskaalan; vain alanavigointi katkaistaan 1.3×:ään.

## Toteutuksen kannalta huomioitava
1. `SettingsSectionCard` korvataan kaistakomponentilla (`SectionBand`): otsikkorivi + sisältö + `HorizontalDivider(color = outline)`. Kortin `surfaceVariant.copy(alpha = 0.4f)` -täyttö poistuu.
2. `DeviceInfoRow` (label vasen / arvo oikea) muuttuu tässä suunnassa pystysuuntaiseksi `StackedMetricRow`:ksi. `LabelValueRow` jää muihin välilehtiin, joissa rivimuoto on oikea.
3. Teemaan lisätään omat väritokenit; **dynaaminen väri (Material You) on syytä kytkeä pois tästä näkymästä tai rajata aksenttiin**, koska laskettuja kontrasteja ei voi taata käyttäjän taustakuvasta johdetulla paletilla.
4. Nykyinen `ScreenTimeDonut`-paletti pysyy ennallaan (se on jo validoitu erikseen).

## Assetit
Ei uusia asseteja. Ikonit ovat Material-ikoneita, jotka repossa on jo käytössä
(`Icons.Filled.Home / Apps / PhoneAndroid / Settings / Refresh`,
`Icons.AutoMirrored.Filled.KeyboardArrowRight`). Handoffin HTML käyttää samoja ikoneita
Material Icons -webfontilla vain esityskuvana.

## Tiedostot
- `design/Laitevahti 1b AAA.dc.html` — **tämä suunta**: tumma (2a) ja vaalea (2b), kumpikin normaalilla ja 2.0× fontilla, sekä mitattujen kontrastien taulukot.
- `design/Laitevahti tyylisuunnat.dc.html` — kolme tyylivaihtoehtoa (1a Expressive-kortit, 1b Mittaristo, 1c vaalea laattaruudukko) vertailuun.
- `design/Laitevahti UI.dc.html` — nykyisen UI:n rekonstruktio (Etusivu, Sovellukset, Laite, Asetukset) vertailukohdaksi.
- `design/support.js` — HTML-tiedostojen ajonaikainen apuri.

## Haaraan vieminen (main ei muutu)
```bash
git checkout v1.6.0            # tai: git checkout -b design/ui-1b-aaa v1.6.0
git checkout -b design/ui-1b-aaa
mkdir -p docs/design
cp -r design_handoff_ui_1b_aaa docs/design/ui-1b-aaa
git add docs/design/ui-1b-aaa
git commit -m "docs(design): UI-suunta 1b (mittaristo) tumma+vaalea, WCAG AAA -paletti"
git push -u origin design/ui-1b-aaa
```
Claude Code lukee tämän jälkeen `docs/design/ui-1b-aaa/README.md`:n suoraan haarasta:
`claude "lue docs/design/ui-1b-aaa/README.md ja toteuta OverviewTab sen mukaan"`.
Älä avaa pull requestia mainiin ennen kuin suunta on hyväksytty.
