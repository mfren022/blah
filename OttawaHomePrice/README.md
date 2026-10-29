# Ottawa Home Price

An Android app that estimates the **average home price at any address in Ottawa, in any month from
1914 to 2060**. Type an address, a postal code or a neighbourhood, or tap the map. Then pick a month
and a type of home, and the app shows:

- the estimated average price of homes like those around that spot, with a likely range;
- how it compares with Ottawa's average that month, and the change over the year before;
- the same price in today's dollars (after inflation);
- a chart of the estimate over the whole period. Drag along it to move through time;
- a map of the city shaded by relative price. Tap anywhere on it to estimate that spot.

It is an estimate for an area, not an appraisal of a particular house.

## Install on your phone

1. Download `OttawaHomePrice.apk` from **Actions** → the latest green *Ottawa Home Price APK* run →
   **Artifacts** → `OttawaHomePrice-apk` (a zip containing the APK).
2. Open the APK on your phone. If Android asks, allow your browser or file manager to install unknown apps.
3. Launch **Ottawa Home Price**.

Every build is signed with the same sideloading key (`app/signing/sideload.keystore`), so new versions
install over the old one and keep your recent searches.

## How the estimate works

**price = Ottawa average for the month × how the spot compares with the city × type-of-home ratio**

1. **Ottawa's average, month by month** (`core/CityIndex.kt`)
   - Yearly average MLS® sale prices of all homes sold through the Ottawa Real Estate Board,
     1956–2025, condos included (`data/annual.csv`). Each sits at mid-year, joined by a monotone cubic
     curve on log prices, so a run of rising years never dips between them.
   - Past the last full year, the latest month's level carries the last point forward at the
     year-over-year change of the latest months (`data/monthly.csv`). That keeps it free of the
     spring peak.
   - Before 1956 the series is carried back with Canada's consumer price index (`data/cpi.csv`), i.e.
     assuming no real change in prices (low confidence).
   - After the latest data it grows at an assumed 3.5% a year (`data/meta.csv`). This is a
     long-run assumption, not a forecast.
2. **How the spot compares with the city** (`core/LocationModel.kt`)
   - Each neighbourhood, community and village in `data/neighbourhoods.csv` has a price level relative
     to the city average and a radius for its extent.
   - The level at any point is a distance-weighted blend of nearby levels, averaged in log space and
     weighted by the reliability of each figure.
   - The map's shading is this blend.
3. **Type of home** (`data/types.csv`): city-wide ratios of detached, semi-detached, townhouse and
   condo-apartment prices to the all-homes average.
4. **Likely range**: about two chances in three. It combines the uncertainty of the month's city
   average (a few percent in the sales era, much more when back-cast or projected), of the area's
   level and of the type ratio.

The app's **How it works** screen explains the same, with every data source.

## Address search

- **Offline, instant:** neighbourhood names, postal Forward Sortation Areas (`K1S`, `K2J`…) and taps
  on the map.
- **Online:** street addresses go to the phone's own geocoder (on most phones backed by Google), then to
  OpenStreetMap's Nominatim if that finds nothing. Results outside the City of Ottawa (Gatineau, for
  instance) are rejected.
- **Without a network:** an address still resolves to its postal code's area, or to a neighbourhood
  named in it.

Nothing else leaves the phone.

## Data

All figures live in `app/src/main/assets/data/*.csv`, each with its sources in `sources.csv`. New
months or neighbourhoods can be added without touching the code. The engine tests check every file
(`core/src/test/.../ShippedDataTest.kt`): continuous years, every neighbourhood inside the city
boundary, sane ratios, known places inside and outside the boundary, and so on.

| File | What |
|------|------|
| `annual.csv` | OREB yearly average sale price, all homes, 1956–2025 |
| `monthly.csv` | Recent monthly averages (each with the same month a year earlier) |
| `cpi.csv` | Canada CPI, yearly, 1914 onward (2002 = 100) |
| `neighbourhoods.csv` | Neighbourhoods: centre, radius, price level vs the city, confidence, basis |
| `postal_areas.csv` | Ottawa postal FSAs and their centres |
| `boundary.csv`, `map.csv` | City boundary; rivers, canal, highways and labels for the map |
| `types.csv` | Type-of-home ratios |
| `meta.csv` | Data month, range of months offered, projection and uncertainty settings |
| `sources.csv` | Every source cited by the files above |

### How good the data is

- **Price history: strong.** The 1956–2021 yearly averages come from OREB's historical table. Two
  independent reconstructions matched it year for year, from the table and from two republished
  copies. Each figure for 2022–2025 comes from an OREB year-end release.
  - 2022 and 2025 are derived from published figures, as `annual.csv` notes.
  - In September 2023 OREB switched to CREA-compiled statistics. That shifts the level by about
    2–3%, well inside the likely range.
- **Latest month: thin.** `monthly.csv` holds only September 2026 and the same month a year earlier.
  Adding OREB's other monthly figures sharpens the latest level, and the engine uses a full
  trailing twelve months as soon as they are there.
- **CPI: good.** Most years are Statistics Canada's published series. A few early years are
  reconstructed and marked `low` in `cpi.csv`. Past 2025 the index assumes 2% inflation.
- **Neighbourhood levels: approximate.** Each of the 195 areas was estimated from general
  knowledge of Ottawa's market (typical 2021–2025 values) and audited region by region. None is
  yet checked against census or sales figures, so all are marked `low` with no source. Expect
  10–20% errors between nearby areas. The likely range and the *How it works* screen say so.
  Replacing them with 2021 Census dwelling values by neighbourhood (or MLS® district averages)
  is the most useful improvement. It only needs `neighbourhoods.csv` edited.
- **Geography: approximate.** The boundary, rivers, highways and postal-area centres are about
  1 km accurate. The tests check that every village is inside the city and every neighbouring
  town is outside.

### Limitations

- Area levels are fixed over time. Neighbourhoods that gentrified or slipped relative to the city
  are shown at today's relative level in every year.
- Before 1956 the city average follows inflation only. After the latest data it follows an
  assumed 3.5% a year. Both are labelled in the app and carry wide ranges.
- Type-of-home ratios are city-wide and fixed over time. Condo apartments have lost ground since
  2023.

## Project layout

```
OttawaHomePrice/
  core/            Engine: data files, monthly index, neighbourhood blend, search, formatting (pure Kotlin + tests)
  app/             Android app (framework views, no AndroidX)
    src/main/assets/data/   The data (CSV)
    src/test/               Robolectric UI tests; they save screenshots of every screen
  tools/sdkless/   Builds and tests the app with only a JDK (no Android SDK)
```

## Building

### With the Android SDK (Android Studio, CI)

```
./gradlew :app:assembleRelease                # APK in app/build/outputs/apk/release
./gradlew :core:test :app:testDebugUnitTest   # engine, data and Robolectric UI tests (JDK 21)
```

### Without the Android SDK (`tools/sdkless`)

```
gradle -p tools/sdkless assembleApk   # tools/sdkless/build/outputs/OttawaHomePrice.apk, signed and verified
gradle -p tools/sdkless test          # all tests; UI screenshots land in tools/sdkless/build/screenshots
```

Requires JDK 17+ (21 for the UI tests). minSdk 26, targetSdk 35.
