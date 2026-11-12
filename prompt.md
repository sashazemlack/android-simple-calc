# ROLE

Act as a senior Android developer, Kotlin engineer, and UI/UX designer.

Your task is to prepare a minimal command-line Android development environment and build a fully functional, native Android calculator application.

Actually create the project, write all necessary code, configure dependencies, run tests, fix errors, and generate installable APKs.

Work autonomously. Do not stop at planning or generating example code.

# 1. ANDROID PLATFORM REQUIREMENTS

Use:

- Language: Kotlin
- UI: Jetpack Compose, using custom UI components
- Build system: Gradle Kotlin DSL
- Minimum SDK: Android 7.0 (API 24)
- Target SDK: Latest stable Android API available when building
- Compile SDK: Same as latest stable Android API
- Architecture: Lightweight MVVM where appropriate

As of November 2026, Android 17 (API 37) is the latest stable target. Use API 37 unless a newer stable release is available when the project is actually built.

Do not use preview or beta SDKs.

Maintain compatibility with Android 7.0 and newer.

Use current stable compatible versions of Kotlin, Gradle, Android Gradle Plugin, Compose, and other dependencies. Verify compatibility using official release documentation.

# 2. NO ANDROID STUDIO AND NO GOOGLE SERVICES

I do not want Android Studio or any other IDE installed.

Prepare a minimal command-line development environment.

Inspect the current machine and determine:

- Operating system.
- Shell.
- Java/JDK installation.
- JAVA_HOME.
- Android SDK.
- ANDROID_HOME / ANDROID_SDK_ROOT.
- Android platform tools.
- Android build tools.
- Gradle.
- Git.
- Connected Android devices through ADB.

Install only missing components necessary to build and test the project.

Use official distribution sources.

Use a compatible JDK, Android SDK command-line tools, platform tools, and Android SDK platform.

Do not install:
- Android Studio.
- An IDE.
- An emulator unless explicitly necessary and authorized.
- Unnecessary graphical development tools.

Prefer user-scoped installations. Do not use administrator privileges without authorization.

Generate a Gradle wrapper.

### Google dependency restrictions

Neither application may depend on Google runtime services or proprietary Google APIs.

Strictly prohibit:

- Google Play Services.
- Firebase.
- Google Maps APIs.
- Google Sign-In.
- Google Analytics.
- Google AdMob.
- Firebase Cloud Messaging.
- Google Play Integrity.
- Other proprietary Google cloud APIs.

Do not use Google APIs for exchange rates.

Android SDK and necessary AndroidX/Jetpack libraries are permitted. They are development libraries and Android platform components, not a requirement to connect the application to Google services.

The application must work independently of Google Play Services.

# 3. TWO APPLICATION VARIANTS

Create one shared repository with two independently buildable application variants.

## Variant A: Calculator Offline

Application name: Calculator Offline.

Features:
- Standard calculator.
- Scientific calculator.
- Calculation history.
- Unit converter.
- Light and dark themes.
- Local persistent settings.

It must work completely offline.

**IMPORTANT: The Offline variant must contain absolutely no exchange-rate feature or exchange-rate-related application code.**

This means:

- No Exchange Rate tab.
- No currency converter screen.
- No currency models.
- No exchange-rate repositories.
- No exchange API clients.
- No exchange-related dependencies.
- No exchange-related resources.
- No currency-related placeholder screens.
- No exchange networking functionality.
- No INTERNET permission.

Do not merely hide the Exchange tab using a Boolean condition.

Exclude the exchange feature from the Offline build at the source-set and dependency level.

## Variant B: Calculator Exchange

Application name: Calculator Exchange.

Features:
- Everything from Calculator Offline.
- Exchange Rate tab.
- Currency selection.
- Automatic currency conversion.
- Daily exchange-rate updates.
- Local exchange-rate caching.
- Manual pull-to-refresh.

The exchange feature must exist exclusively in this variant.

### Variant isolation

Use Gradle product flavors.

Suggested organization:

app/src/main/
app/src/offline/
app/src/exchange/

Shared code belongs in main:
- Calculator.
- Scientific calculator.
- History.
- Unit converter.
- Shared UI primitives.
- General settings.

Exchange-specific implementation belongs exclusively in exchange:
- Currency data.
- Exchange models.
- API client.
- Networking.
- Exchange repository.
- Exchange UI.
- Exchange-specific resources.

Use a flavor-specific application root so the Offline application does not reference Exchange classes at compile time.

Only the Exchange flavor may include the INTERNET permission in its merged manifest.

Verify the final merged manifests and APK contents.

The Offline APK must not contain exchange packages, exchange resources, currency API dependencies, or network permissions.

Give the applications separate application IDs so they can be installed simultaneously.

# 4. VISUAL DESIGN — VERY IMPORTANT

I strongly dislike Material Design.

Do NOT use Material Design 3 or Material Design 2.

Do not use:
- Material Components.
- Material typography.
- Material buttons.
- Material cards.
- Floating action buttons.
- Large pill-shaped controls.
- Excessively rounded cards.
- Material tonal surfaces.
- Material elevation and shadows.
- Material navigation bars.
- Material dynamic color schemes.
- Default Material icons.

Build the visual design yourself using Compose Foundation, Compose UI, Compose Animation, and custom drawing where necessary.

### Design reference

Use the visual language of legacy Windows 10 Fluent / Windows 10 UWP around 2016–2017.

Secondary inspiration: classic Android 4 Holo interface.

Prefer Windows 10 Fluent as the main direction.

Design characteristics:

- Flat rectangular controls.
- Restrained corner radii (approximately 2–4 dp).
- Crisp separators.
- Thin borders.
- Subtle hover, pressed, and focus states.
- Simple typography.
- Clean alignment.
- Strong readability.
- Flat surfaces rather than elevated cards.
- Restrained blue accent color.
- Minimal animation.

Suggested visual direction:

Light theme:
- White or very light gray backgrounds.
- Dark gray text.
- Light gray separators.
- Restrained blue accent.

Dark theme:
- Very dark gray backgrounds.
- Slightly lighter input and keypad surfaces.
- Light gray text.
- Subtle separators.
- Same blue accent.

Use native Android system sans-serif typography.

Do not bundle unnecessary font files or third-party icon packs.

Use simple line icons or custom-drawn symbols only when needed.

The result should look like a practical, understated Windows 10-era utility application, not a contemporary Material calculator.

### Themes

Support:
- Light.
- Dark.
- System default.

When System is selected, synchronize with the Android system appearance.

Do not force light or dark mode.

Persist the user's selected theme preference if they explicitly override the system setting.

# 5. MAIN NAVIGATION

Use top tabs.

Calculator Offline:
1. Calculator
2. Unit Converter

Calculator Exchange:
1. Calculator
2. Exchange Rate
3. Unit Converter

The tabs should be simple, flat, and compact.

Use a restrained underline or indicator to identify the current tab.

Switching tabs should use a subtle animation.

Preserve relevant input state when switching tabs.

# 6. CALCULATOR

Create a proper calculator screen.

### Display

At the top, show:

- Current expression/input line.
- Live calculated result underneath.

The result must update while the user types.

It must not require pressing =.

Example:

Input:
125 + 25 * 2

Live result:
175

When the user presses =:

1. Evaluate the expression.
2. Save the calculation in persistent history.
3. Replace the input with the calculated result.
4. Allow subsequent calculations to continue from that result.

Entering a new number after completing a calculation starts a new expression.

Entering an operator continues from the previous result.

Handle incomplete and invalid expressions gracefully.

### Action row

Include:

- History.
- Paste.
- Copy.

History opens an expandable history panel that occupies the keypad area.

Paste reads a valid expression from the Android clipboard.

Copy copies the current evaluated result.

Display brief feedback when an action succeeds or fails.

### Standard keypad

Use four columns.

First row:

| C | % | Backspace | / |

Second row:

| 7 | 8 | 9 | * |

Third row:

| 4 | 5 | 6 | - |

Fourth row:

| 1 | 2 | 3 | + |

Fifth row:

| +/- | 0 | . | = |

Keep the right-hand operator column visually distinguishable.

Buttons should be flat, consistent, readable, and comfortable to press.

# 7. SCIENTIFIC KEYPAD

Implement an expandable scientific keypad.

It must be collapsed by default.

Above the standard keypad, show a small horizontal handle or expander.

The user can drag the handle downward to expand the scientific keypad.

Dragging upward collapses it again.

Tapping the handle also toggles expansion.

Use smooth but restrained height animation.

When expanded, the scientific keypad must contain five columns and three rows:

| sin | cos | tan | log | ln |
|---|---|---|---|---|
| ( | ) | ^ | √ | ! |
| π | e | inv | RAD/DEG | Empty |

Functions:

- Sine.
- Cosine.
- Tangent.
- Base-10 logarithm.
- Natural logarithm.
- Exponents.
- Square roots.
- Factorials.
- Pi constant.
- Euler's number.

The inv button switches appropriate functions:

- sin → asin
- cos → acos
- tan → atan
- log → 10^x
- ln → e^x

RAD/DEG toggles the angle mode.

Make the selected mode clearly visible.

# 8. MATHEMATICAL ENGINE

Implement a safe mathematical expression parser.

Never use eval(), JavaScript execution, or unsafe string evaluation.

Use an established mathematical library if it genuinely meets the requirements, or write a tokenizer and parser.

Support:

- Addition.
- Subtraction.
- Multiplication.
- Division.
- Percentages.
- Modulo.
- Parentheses.
- Decimals.
- Negative numbers.
- Exponents.
- Square roots.
- Factorials.
- Trigonometric functions.
- Inverse trigonometric functions.
- Logarithms.
- Natural logarithms.
- Pi.
- Euler's number.
- Degrees and radians.
- Mathematical operator precedence.
- Implicit multiplication where appropriate.

Handle invalid expressions, division by zero, invalid roots, logarithm domain errors, and invalid factorial inputs.

Avoid unnecessary rounding errors.

Display readable results.

The calculator must remain responsive during live evaluation.

Write comprehensive unit tests.

# 9. CALCULATION HISTORY

Use persistent local storage.

Each entry must contain:

- Original expression.
- Result.
- Timestamp.

Keep up to 100 recent calculations.

History must survive restarts.

The history panel replaces the keypad area when expanded.

Show entries newest first.

Allow the user to:

- Tap an entry to restore it.
- Clear all history.
- Return to the keypad.

Restoring an entry must not create a new history record.

# 10. EXCHANGE RATE SCREEN

Implement this screen exclusively in Calculator Exchange.

The screen should have a layout consistent with the calculator but adapted to currency conversion.

Include:

- Amount entry.
- Source currency.
- Destination currency.
- Swap control.
- Converted value.
- Exchange rate information.
- Rate update status.
- On-screen numeric keypad.

Currency selectors must be searchable.

Use standard currency codes and recognizable currency names.

Include commonly used currencies, including:

- EUR.
- USD.
- GBP.
- CZK.
- JPY.
- CHF.
- PLN.

Support all currencies provided by the selected API, subject to the provider's actual coverage.

### Automatic conversion

The result must update automatically when:

- Amount changes.
- Source currency changes.
- Destination currency changes.
- The currencies are swapped.

No equals button should be necessary.

Perform the calculation locally using downloaded rate data.

Do not send the user's entered amount to the exchange provider.

### Exchange-rate provider

Select a reputable NON-GOOGLE exchange-rate API.

Prefer a provider that:
- Does not require an API key.
- Has documented endpoints.
- Has transparent update frequency.
- Provides a reasonable currency selection.
- Allows the intended use.
- Has reasonable availability.

Verify the provider's current documentation, limitations, and licensing before implementation.

Do not use Google Finance or Google APIs.

### Daily update policy

Normal automatic refresh must happen at most once every 24 hours.

Do not continuously poll the provider.

Do not refresh the rates every time the user switches tabs.

When the Exchange Rate tab is opened or becomes active, check whether the last automatic refresh attempt is at least 24 hours old.

If it is, retrieve updated rates.

Otherwise, use the locally cached data.

Persist the relevant refresh timestamps so restarting the application does not reset the refresh schedule.

### Manual pull-to-refresh

Implement a pull-down gesture directly on the Exchange Rate screen.

The user must be able to swipe from top to bottom to trigger an immediate refresh.

This manual gesture must bypass the normal 24-hour automatic-refresh limit.

Requirements:

- Detect the gesture only when the Exchange screen is at its top position.
- Show a small refresh indicator.
- Perform the network request.
- Update the locally cached rates on success.
- Update the displayed timestamp.
- Show an error state if refreshing fails.
- Do not block calculator or unit converter functionality during refresh.

Implement this interaction without Material pull-to-refresh components.

### Last-updated information block

This requirement is important.

Place a simple textual status block directly ABOVE the on-screen numeric keypad.

It must always communicate the state of the exchange-rate data.

Examples:

"Rates last updated: 11 Nov 2026, 09:15"

"Using cached rates · Last updated: 10 Nov 2026, 18:30"

"Refreshing exchange rates..."

"Exchange rates unavailable. Swipe down to retry."

Use the actual current retrieval timestamp and the provider's effective rate date when available.

Do not confuse the provider's publication date with the time the application downloaded the rates.

Make stale rates clearly distinguishable from recently retrieved rates.

### Offline behavior

If there is no internet connection:

- Continue using cached rates.
- Show the last successful update.
- Clearly identify the rates as cached.
- Do not pretend that stale rates are live.
- If there is no cache, display a clear unavailable state.

Do not crash or block the interface.

# 11. UNIT CONVERTER

This feature exists in BOTH variants.

It must be entirely offline.

### Category selection

Show a simple responsive grid containing:

1. Length.
2. Area.
3. Volume.
4. Speed.
5. Weight / Mass.
6. Temperature.
7. Power.
8. Pressure.

Prefer two columns on a typical phone.

Use simple icons and flat rectangular grid items.

### Conversion screen

After selecting a category, show:

- Amount input.
- Source-unit selector.
- Destination-unit selector.
- Swap button.
- Converted value.
- Numeric input keypad.

The conversion happens automatically when an input changes.

### Supported units

Length:
- Millimeter.
- Centimeter.
- Meter.
- Kilometer.
- Inch.
- Foot.
- Yard.
- Mile.

Area:
- Square meter.
- Square kilometer.
- Hectare.
- Acre.
- Square foot.
- Square mile.

Volume:
- Milliliter.
- Liter.
- Cubic meter.
- US gallon.
- US quart.
- US fluid ounce.
- Cup.

Speed:
- Meter per second.
- Kilometer per hour.
- Mile per hour.
- Foot per second.
- Knot.

Weight / Mass:
- Milligram.
- Gram.
- Kilogram.
- Metric tonne.
- Ounce.
- Pound.

Temperature:
- Celsius.
- Fahrenheit.
- Kelvin.

Power:
- Watt.
- Kilowatt.
- Megawatt.
- Metric horsepower.
- Mechanical horsepower.

Pressure:
- Pascal.
- Kilopascal.
- Bar.
- Atmosphere.
- PSI.
- mmHg.

### Conversion engine

Use proper conversion formulas.

Normalize linear conversions through canonical base units.

Temperature conversions must use proper offset-based formulas.

Pay special attention to:
- Temperature.
- Area scaling.
- Volume.
- Horsepower variants.
- Pressure.
- Floating-point precision.

Do not hardcode inaccurate conversion factors.

Write tests for all eight categories.

# 12. PERSISTENCE

Use local persistent storage.

Persist:

- Calculator history.
- Angle mode.
- Theme preference.
- Last selected unit category.
- Last selected units.
- Relevant calculator preferences.
- Cached exchange-rate information in the Exchange flavor only.

Use Room, DataStore, or another lightweight appropriate AndroidX solution.

Do not store unnecessary personal information.

# 13. ARCHITECTURE AND DEPENDENCIES

Keep the codebase lightweight and understandable.

Use:

- Kotlin.
- Jetpack Compose Foundation and UI.
- Compose Animation.
- AndroidX Activity Compose.
- Coroutines and Flow.
- Room where appropriate.
- DataStore where appropriate.

Do not use any Material dependencies.

Separate responsibilities into:

- UI.
- ViewModels.
- Calculator engine.
- Unit conversion engine.
- History repository.
- Preferences.
- Exchange repository and API client (Exchange only).

Keep exchange dependencies out of the Offline variant completely.

Avoid overengineering.

Do not introduce unnecessary dependency injection frameworks, networking abstractions, or large libraries.

# 14. TESTING

Implement and execute automated tests.

Test:

Calculator:
- Arithmetic.
- Precedence.
- Parentheses.
- Negative values.
- Decimals.
- Percentage.
- Exponents.
- Factorials.
- Trigonometric functions.
- Inverse functions.
- Degrees/radians.
- Invalid expressions.
- Division by zero.

Unit converter:
- Every category.
- Every important conversion pair.
- Temperature conversions.
- Precision.

Exchange:
- Rate parsing.
- Currency selection.
- Currency swapping.
- Conversion calculations.
- Cache persistence.
- Daily refresh throttling.
- Forced pull-to-refresh.
- Failure handling.

Check both flavors independently.

Verify:

- Both variants build.
- Both variants install independently.
- Offline application has no INTERNET permission.
- Offline application contains no Exchange classes or resources.
- Exchange application has the required networking permission.
- No Google Play Services dependencies exist.
- No Material dependencies exist.
- Dark and light themes work.
- Android 7 compatibility is maintained.
- The latest target SDK behavior is tested.
- No crashes during ordinary interactions.

# 15. BUILD OUTPUT

Build:

- assembleOfflineDebug
- assembleExchangeDebug
- testOfflineDebugUnitTest
- testExchangeDebugUnitTest

Use ADB to install both APKs if a physical device is connected.

Do not claim successful installation unless actually verified.

Generate the APKs in the standard Gradle output directories.

# 16. DOCUMENTATION

Create README.md.

Explain:

- Project architecture.
- Build environment preparation.
- SDK configuration.
- Building the Offline variant.
- Building the Exchange variant.
- Running tests.
- Installing with ADB.
- APK locations.
- Exchange API provider.
- Exchange-rate update schedule.
- Manual refresh behavior.
- Offline caching.
- Runtime dependencies and permissions.

# 17. FINAL EXECUTION INSTRUCTIONS

Do the actual work.

Do not just describe how to do it.

Do not stop after creating the project structure.

Do not leave TODOs for core features.

Do not provide mock functionality in place of working features.

Do not add an Exchange tab or related implementation to the Offline variant.

Do not introduce Material Design.

Do not introduce Google runtime dependencies or services.

Do not install an IDE.

Build, test, and fix the project.

If a particular component cannot be completed because of an environmental limitation, explain the precise reason and complete all remaining work.

At the end, report:

1. Environment successfully prepared.
2. Project directory.
3. Offline APK location.
4. Exchange APK location.
5. Tests executed and their results.
6. Device installation results, if applicable.
7. Any remaining limitations.

Begin by inspecting the machine and preparing the minimal command-line Android environment.