# SCalc: Calculator Offline & Calculator Exchange

One repository builds two native Android calculator apps, using Gradle product flavors:

| Variant | Application ID | Tabs | Network |
|---|---|---|---|
| **Calculator Offline** | `com.scalc.calculator.offline` | Calculator · Unit Converter | none (no `INTERNET` permission) |
| **Calculator Exchange** | `com.scalc.calculator.exchange` | Calculator · Exchange Rate · Unit Converter | HTTPS only, for daily exchange rates |

Both apps can be installed side by side. Neither one uses Google Play Services, Firebase or any other Google API at runtime. Both were installed and tested on a Pixel 10a running GrapheneOS (Android 17, API 37), which has no Google Play Services.

The UI uses only Compose **Foundation/UI/Animation**, plus a small in-house design system modelled on **Windows 10 Fluent (2016–2017)** with a light Holo influence:
- flat rectangular controls with 2–4 dp corners;
- 1 dp separators;
- a restrained blue accent (`#0078D7`);
- the system sans-serif font;
- custom line icons drawn on a Canvas.

There are **no Material dependencies**: no Material components, typography, icons, ripple, elevation or dynamic color. Light, Dark and System themes are available in Settings (the gear icon at the top right). Choosing Light or Dark is saved.

---

## 1. Architecture

```
app/src/
├── main/                       shared by both apps (no exchange code, no permissions)
│   ├── AndroidManifest.xml     no permissions, backup off, emoji2/Play-Services font initializer removed
│   ├── kotlin/com/scalc/calculator/
│   │   ├── CalcApplication.kt  manual DI container (DataStore singletons)
│   │   ├── MainActivity.kt     edge-to-edge single activity
│   │   ├── AppTab.kt           tab descriptor supplied by the flavor root
│   │   ├── ui/                 AppRoot (tab host), theme/ (Fluent colors, typography, focus/press indication),
│   │   │                       components/ (keys, tabs, selector, picker dialog, numeric keypad, glyphs, feedback)
│   │   ├── common/             NumberFormatting, AmountInput (shared keypad input model)
│   │   ├── settings/           SettingsRepository (Preferences DataStore), SettingsDialog
│   │   ├── calc/engine/        tokenizer → recursive-descent parser → BigDecimal evaluator, ExpressionEditor
│   │   ├── calc/history/       HistoryRepository (DataStore, versioned binary format, 100 entries)
│   │   ├── calc/ui/            CalculatorViewModel, CalculatorScreen, scientific panel, history panel
│   │   └── converter/          engine/ (exact factors), ui/ (category grid, conversion screen, ViewModel)
│   └── res/                    shared strings, window themes, launcher icon, backup exclusion rules
├── offline/                    flavor root: Flavor.kt (2 tabs), app name, about text
├── exchange/                   the ONLY place where exchange-rate code exists
│   ├── AndroidManifest.xml     INTERNET permission + network security config (HTTPS only)
│   ├── kotlin/com/scalc/calculator/flavor/Flavor.kt   (3 tabs)
│   ├── kotlin/com/scalc/calculator/exchange/{model,data,ui}/
│   └── res/                    exchange strings, launcher badge, network_security_config.xml, ECB root cert
├── test/                       JVM unit tests for shared code (run in both flavors)
└── testExchange/               JVM unit tests for the exchange feature only
```

**Flavor isolation.** The shared shell, `ui/AppRoot.kt`, never refers to exchange classes. It renders whatever `com.scalc.calculator.flavor.Flavor.tabs` returns.
- That object is defined twice, in `src/offline` and in `src/exchange`, and each build compiles only its own copy. The Offline build therefore cannot reference exchange code at compile time; the feature is absent from it, not hidden behind a flag.
- The same split covers:
  - the `INTERNET` permission and the network security config (exchange manifest only);
  - the exchange resources;
  - the exchange-only test dependency (`testExchangeImplementation(org.json)`).
- `scripts/verify-flavors.ps1` checks all of this in the built APKs (see §4).

**Lightweight MVVM.**
- Each tab has an activity-scoped `ViewModel` that exposes a `StateFlow` UI state.
- ViewModels are created with a `viewModelFactory` from a manual `AppContainer`; there is no DI framework.
- Activity scope keeps typed input intact when you switch tabs. The calculator also keeps its expression, inverse mode and history-panel state in a `SavedStateHandle`, so they survive process death.
- Heavy work runs off the main thread: evaluation on `Dispatchers.Default`, disk and network on IO.
- The pure logic is unit-tested on the JVM:
  - engine and editor;
  - converter;
  - rate parsing;
  - refresh policy;
  - status formatting.

**Persistence.** AndroidX DataStore stores everything; no Room or KSP is needed.
- `settings.preferences_pb`: theme mode, angle mode (DEG/RAD), last unit category and whether its screen was open, last From/To units per category.
- `history.bin`: up to 100 calculations, newest first. Each entry stores the expression, result and timestamp. When the expression continued from a rounded previous result, it also stores that result's exact 34-digit value, so a restored entry recomputes identically. The format is versioned binary (v2; v1 files are still read). Corrupt files are reset.
- `exchange_cache.bin` (Exchange only): the last rate table with currency names, the provider's rate date, the download time, ETag, refresh timestamps and the selected currencies.

Typed amounts are never persisted. Nothing personal is stored. App backup and device-to-device transfer are both disabled (`allowBackup="false"` plus `data_extraction_rules.xml`), so no data leaves the device.

The UI is English-only and always left-to-right (`supportsRtl="false"`), because keypads and expressions only make sense left to right.

## 2. Build environment (command line only, no IDE)

The build environment was prepared on Windows 11 without Android Studio. Everything is installed user-scoped, with no administrator rights:

| Component | Version | Location |
|---|---|---|
| JDK | Eclipse Temurin 21.0.13+9 (LTS) | `%LOCALAPPDATA%\Programs\Java\jdk-21.0.13+9` |
| Android SDK command-line tools | latest (23.0, now fronted by the "Android CLI") | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| SDK Platform | `platforms;android-37.0` (Android 17, API 37) | `%LOCALAPPDATA%\Android\Sdk\platforms` |
| Build tools | `build-tools;37.0.0` | `%LOCALAPPDATA%\Android\Sdk\build-tools` |
| Platform tools (adb) | 37.0.3 (already installed via winget) | on `PATH` |
| Gradle | 9.9.1 via the Gradle wrapper (SHA-256 pinned) | `~/.gradle/wrapper/dists` |

User environment variables:
- `JAVA_HOME` points to the Temurin 21 directory.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` point to `%LOCALAPPDATA%\Android\Sdk`.

A Java 26 that was already on `PATH` is left untouched; Gradle uses `JAVA_HOME`.

To reproduce on another machine:

1. Download Temurin 21 (https://adoptium.net) and the SDK command-line tools (https://developer.android.com/studio#command-line-tools-only). Extract them and set the variables above.
2. Install the SDK packages (accept the SDK license when prompted):
   ```
   %ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
   ```
   In cmdline-tools 23 this delegates to `android sdk install platforms/android-37.0 build-tools/37.0.0`. Add `--no-metrics` to keep usage metrics off.
3. **SDK configuration:** create `local.properties` containing `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`, or rely on `ANDROID_HOME`.

### Toolchain versions (latest stable, checked 2026-11-11)

| | Version |
|---|---|
| Android Gradle Plugin | 9.5.1 (built-in Kotlin support, so `org.jetbrains.kotlin.android` is not applied) |
| Kotlin / Compose compiler plugin | 2.4.21 (KGP pinned on the root buildscript classpath) |
| Gradle | 9.9.1 |
| Compose BOM | 2026.09.00 (Compose UI/Foundation/Animation 1.12.1) |
| AndroidX | activity-compose 1.13.0, lifecycle 2.11.0, datastore 1.3.0, core-ktx 1.19.1 |
| Coroutines | 1.11.0 |
| SDK | `minSdk 24` (Android 7.0), `compileSdk 37`, `targetSdk 37`, build-tools 37.0.0 |

## 3. Building

```
gradlew.bat assembleOfflineDebug      # Calculator Offline
gradlew.bat assembleExchangeDebug     # Calculator Exchange
```

**APK locations** (standard Gradle output directories):

- `app/build/outputs/apk/offline/debug/app-offline-debug.apk`
- `app/build/outputs/apk/exchange/debug/app-exchange-debug.apk`

Release builds (`assembleOfflineRelease`, `assembleExchangeRelease`) use R8 minification and resource shrinking. They are unsigned until you add your own signing config.

## 4. Tests and checks

```
gradlew.bat testOfflineDebugUnitTest      # shared tests
gradlew.bat testExchangeDebugUnitTest     # shared tests + exchange tests
gradlew.bat lintOfflineDebug lintExchangeDebug   # includes NewApi checks against minSdk 24; abortOnError = true
powershell -ExecutionPolicy Bypass -File scripts\verify-flavors.ps1   # APK isolation checks
```

Current results:
- `testOfflineDebugUnitTest`: **539 tests, 0 failures**.
- `testExchangeDebugUnitTest`: **675 tests, 0 failures**. This is the 539 shared tests plus 136 exchange tests.
- Both lint tasks report **"No issues found"**.
- `verify-flavors.ps1` passes all **17 checks**.

The tests cover:
- **Engine:** arithmetic and precedence, percent and modulo, powers, roots, factorials, trigonometry in DEG and RAD, logarithms, implicit multiplication, errors, number formatting, paste normalization, and a seeded fuzz test of the editor.
- **Calculator ViewModel:** live result, `=` and carry, history restore without a new record, paste and copy, inverse and RAD/DEG keys, persistence, process-death state, and layout math.
- **Unit converter:** every category, all 280 ordered unit pairs round-tripped, temperature offsets, and ViewModel persistence.
- **Exchange:** Frankfurter and ECB parsers, the HTTP client (ETag/304, fallback, errors, HTTPS only), the cache serializer and corruption, the 24 h policy (clock changes, failures, forced refresh), status texts, the ViewModel (conversion, swap, currency selection, default amount, stale ticking), the pull-to-refresh state machine and the network security config.

`scripts/verify-flavors.ps1` inspects the built APKs with `aapt2` and `apkanalyzer`. It checks that:
- the Offline APK has no `INTERNET` or network-state permission;
- the Offline APK has no `com.scalc.calculator.exchange` classes, no exchange string resources and no provider URLs;
- the Exchange APK has the permission, the classes and the provider URL;
- neither APK contains classes from Material, Play Services, Firebase, Play Core/Play Integrity, Play Billing, ML Kit or other proprietary Google SDKs;
- the application IDs, labels, `minSdk` and `targetSdk` are correct.

## 5. Installing with ADB

```
adb devices
adb install -r app\build\outputs\apk\offline\debug\app-offline-debug.apk
adb install -r app\build\outputs\apk\exchange\debug\app-exchange-debug.apk
adb shell am start -n com.scalc.calculator.offline/com.scalc.calculator.MainActivity
adb shell am start -n com.scalc.calculator.exchange/com.scalc.calculator.MainActivity
```

`install -r` keeps app data, so history and settings survive updates. This was verified on the device.

## 6. Calculator (both apps)

**Display and keys.**
- The expression line has a live result underneath that updates as you type; you don't need to press `=`.
- `=` evaluates, saves the calculation to history and replaces the input with the result. A digit typed next starts a new expression; an operator continues from the result. The exact previous value is carried, so `1÷3`, `=`, `×3`, `=` gives exactly 1.
- Unclosed parentheses are closed automatically. `sin(30` gives 0.5 and is saved as `sin(30)`.
- The action row holds History, Paste and Copy, each with brief feedback.
- The 4×5 keypad (`C % ⌫ ÷ / 7 8 9 × / 4 5 6 − / 1 2 3 + / +/− 0 . =`) has a distinct operator column and an accent `=` key.

**Scientific keypad.**
- It is collapsed by default. Drag the handle down (or tap it) to open it.
- The handle sits on the panel's bottom edge, just above the standard keypad. It moves down with the panel as it slides open, and dragging it up (or tapping it) closes it again.
- The keys are:
  ```
  sin  cos  tan  log  ln        inv → sin⁻¹ cos⁻¹ tan⁻¹ 10ˣ eˣ
  (    )    ^    √    !
  π    e    inv  RAD  DEG       the active angle unit is highlighted like inv
  ```
- The angle unit is saved between launches.

**History.** The history panel replaces the keypad area. It keeps 100 entries, newest first, each with a timestamp. Tap an entry to restore it; restoring does not create a new record. You can clear all entries, and Back or the chevron returns to the keypad.

**Engine.** The engine is a hand-written tokenizer, a recursive-descent parser and a `BigDecimal` evaluator with 34 significant digits. There is no `eval`, scripting or reflection.
- **Precedence:** `!` and `%` (postfix), then `^` (right-associative; `−2^2 = −4`), then unary sign, then `× ÷`, modulo and implicit multiplication (`2π`, `3sin(30)`, `(2)(3)`, all at the same level), then `+ −`.
- **Exactness:** `0.1+0.2 = 0.3` and `1.1×3 = 3.3`.
- **Percent:**
  - `50%` = 0.5.
  - Additive percent: `200+10%` = 220.
  - Modulo when an operand follows: `10%3` = 1. A negative divisor needs brackets: `10%(−3)`.
- **Precision and limits:**
  - Results display at 15 significant digits, with `1.5E+20` style for very large and very small numbers.
  - Trigonometric and logarithmic functions are computed in double precision (15–16 correct digits). DEG mode is exact at special angles (sin 30° = 0.5, tan 90° → error).
  - Factorials go up to 25205!.
  - A difference smaller than 10⁻³² of its operands is shown as 0, which removes rounding residue.
- **Errors** shown under the expression: *Invalid expression*, *Incomplete expression*, *Cannot divide by zero*, *Invalid input for this function*, *Factorial needs a whole number ≥ 0*, *Result is too large*.

## 7. Unit converter (both apps, fully offline)

A 2-column grid shows 8 categories; the last one used is marked. Each conversion screen has:
- the amount;
- From and To selectors (choosing the opposite unit swaps them);
- a swap button;
- automatic conversion as you type;
- a one-unit rate hint (a formula for temperature);
- a numeric keypad.

The last category and the last units per category are saved.

All factors are exact definitions, stored as `BigDecimal` ratios to the SI base unit, with one 34-digit division per conversion:

| Category | Units (factor to base) |
|---|---|
| Length (m) | mm, cm, m, km; in = 0.0254, ft = 0.3048, yd = 0.9144, mi = 1609.344 |
| Area (m²) | m², km², ha = 10⁴, ac = 4046.8564224, ft² = 0.09290304, mi² = 2589988.110336 |
| Volume (m³) | mL, L, m³; US gal = 0.003785411784, US qt = ¼ gal, US fl oz = 1/128 gal, US cup = 8 fl oz |
| Speed (m/s) | m/s, km/h = 1000/3600, mph = 0.44704, ft/s = 0.3048, knot = 1852/3600 |
| Weight/Mass (kg) | mg, g, kg, t = 1000; oz = 0.028349523125, lb = 0.45359237 |
| Temperature (K) | °C: K = C + 273.15; °F: K = (F + 459.67) × 5/9; K |
| Power (W) | W, kW, MW; PS (metric hp) = 735.49875; hp (mechanical) = 745.69987158227022 |
| Pressure (Pa) | Pa, kPa, bar = 10⁵, atm = 101325, psi = lbf/in² ≈ 6894.757293168361, mmHg = 133.322387415 |

## 8. Exchange rates (Calculator Exchange only)

**Provider.** The app uses [Frankfurter](https://frankfurter.dev) API v2, restricted to the official **European Central Bank euro reference rates**:
- Rates: `GET https://api.frankfurter.dev/v2/rates?providers=ECB`
- Currency names: `GET https://api.frankfurter.dev/v2/currencies?providers=ECB`

Frankfurter needs no API key and has no quotas. It is MIT-licensed open source and does not log personal data.

The ECB data may be reused freely with attribution. The app shows "Source: European Central Bank via frankfurter.dev · cross rates calculated on device". The ECB says the rates are for information only, not for transactions.

If Frankfurter is unreachable, the app reads the same data directly from the ECB at `https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml`. That host's certificate chain ends at a 2021 Sectigo root that Android 7–13 do not ship. The app therefore bundles that root and trusts it **for this one host only**; all other traffic uses the system trust store, and cleartext is denied.

Coverage is the ECB set of about 30 currencies, including EUR, USD, GBP, CZK, JPY, CHF and PLN, which are pinned at the top of the searchable picker. The ECB publishes once per TARGET working day, around 16:00 CET.

Two other no-key APIs were evaluated and rejected:
- fawazahmed0/exchange-api: its data sources are undocumented.
- open.er-api.com: its terms restrict redistribution and require an attribution link.

**Privacy.** The app downloads the whole rate table and converts locally as `amount × rate[to] ÷ rate[from]`. **The amount you type is never sent.** Requests carry only a generic User-Agent.

**Daily update schedule.**
- An automatic refresh happens **at most once every 24 hours**.
- The check runs when the Exchange Rate tab becomes active, and again when the app returns to the foreground with the tab visible. A refresh is due only if 24 hours have passed since both the last automatic attempt and the last successful download.
- Otherwise the cache is used. There is no polling and no refresh on every tab switch.
- The timestamps are persisted, so restarting the app does not reset the schedule.
- A clock that moves backwards cannot block refreshes indefinitely.
- An unchanged table is detected with ETag / `304 Not Modified`.

**Manual refresh.**
- Pull down anywhere on the Exchange screen while it is scrolled to the top. This refreshes immediately, regardless of the 24-hour limit.
- A small flat indicator follows the pull and spins while the request runs.
- On success the cache and timestamp are updated and "Exchange rates updated" is shown. On failure the status block shows the error.
- The calculator and converter stay usable during a refresh.
- This is a custom NestedScroll implementation, not a Material component. TalkBack users get a "Refresh exchange rates" action on the status block.

**Status block.** A text block directly above the numeric keypad always describes the data. It keeps this app's download time separate from the provider's rate date:
- *Rates last updated: 11 Nov 2026, 22:56* · ECB reference rates of 11 Nov 2026
- *Refreshing exchange rates...*
- *Using cached rates · Last updated: …*: the latest refresh attempt failed (for example offline) and the cached table is shown.
- *Using cached rates (may be outdated) · …*: the data is more than 24 hours old. It is shown in the warning color, and the status turns stale while the app stays open.
- *Exchange rates unavailable. Swipe down to retry.*: there is no cache yet and the download failed.

**Offline caching.** The last successful table is stored in `exchange_cache.bin` and loaded at start. Without a connection, conversions keep working from that cache, which is labelled as cached or outdated. With no cache at all, the app shows a clear unavailable state; it never crashes or blocks. The amount starts at a default of 1, which the first key you type replaces.

## 9. Runtime dependencies and permissions

Runtime libraries (identical in both apps):
- AndroidX Compose: runtime, ui, foundation, animation
- activity-compose
- lifecycle (runtime-compose, viewmodel-compose)
- datastore and datastore-preferences
- core-ktx
- kotlinx-coroutines-android

These bring in their usual AndroidX transitive dependencies, including `com.google.guava:listenablefuture:1.0`, an empty interface artifact.

Neither app depends on Material, Play Services, Firebase or any network library. The Exchange app uses only the platform `HttpURLConnection` and `org.json`. The `androidx.emoji2` startup initializer, which would look up a Play Services font provider, is removed in the manifest.

| Permission | Offline | Exchange |
|---|---|---|
| `android.permission.INTERNET` | — | ✔ |
| `<appId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level, added by androidx.core) | ✔ | ✔ |

## 10. Known limitations

- Android 7.0–16 (API 24–36) compatibility is checked by lint (`NewApi`, `minSdk 24`) and unit tests. It was not run on a physical device or emulator below API 37, because only a Pixel 10a (API 37) was available and no emulator was installed.
- Not exercised on the device, because each would need system-setting changes: airplane-mode behavior of the Exchange app (covered by unit tests), rotation, large system font scales and TalkBack.
- Trigonometric and logarithmic results are accurate to about 15–16 digits (double precision); arithmetic is exact to 34 digits.
- ECB reference rates cover about 30 currencies and are published once per working day.
- Kotlin Gradle Plugin 2.4.21 is newer than the newest Gradle/AGP combination its release notes list as tested. The build is clean, with no warnings.
