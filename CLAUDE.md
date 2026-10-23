# CLAUDE.md

Guidance for Claude Code sessions in this repository.

## Project

**Clock**: an open-source Android clock app (alarms, timers, stopwatch) focused on alarms that
always ring and are highly customisable. It must work on **any supported Android device**, not just
the maintainer's phone.

- Product source of truth: [`docs/PLANO.md`](docs/PLANO.md) (in Portuguese). Read it before
  starting a phase. If something in it looks wrong or outdated (APIs, libraries, permissions), say
  so **before** deviating from it.
- Package / applicationId: `io.github.imlemos.clock` (debug builds add `.debug`).
- Reference device: OPPO Find X9 Pro (CPH2791), ColorOS 16 / Android 16 (API 36).

## Non-negotiable rules

- **Language**: code, comments, identifiers, commit messages and technical docs in English.
  `docs/PLANO.md` stays in Portuguese.
- **No text in code.** Every user-visible string lives in resources with English
  (`values/`, default) and European Portuguese (`values-pt-rPT/`). Missing translations fail lint.
  Non-translatable strings (brand name, sample digits) use `translatable="false"`.
- **No vendor-specific code outside `core/oem`.** Anything OEM-specific goes behind `OemProfile`
  with a generic fallback.
- **Privacy**: no `INTERNET` permission, no analytics, no dependency that talks to the network,
  no storage permissions (pickers + copy into app storage). `verify<Variant>Permissions` enforces
  this on the merged manifest.
- **Versions** live only in `gradle/libs.versions.toml`. Use the latest *stable* releases and
  confirm them online (Google Maven / Maven Central metadata), not from memory.
- **Scheduling logic** (next occurrence, recurrences, time zones, DST) is pure Kotlin in
  `:core:scheduling` with extensive unit tests. Never add Android dependencies there.
- **Secrets** never enter the repo: `keystore.properties`, `*.jks`, `*.keystore` are git-ignored.
- **No LICENSE file** until the maintainer chooses a licence. Bundled assets keep their own
  licences under `licenses/`.
- **Commits**: small and descriptive. **Never push** without asking.

## Architecture

```
:app                         Android app, organised by feature
  alarm/ timer/ stopwatch/   feature screens (empty in phase 0)
  settings/                  settings screen + view model
  trash/                     (phase 2)
  navigation/                Navigation 3 keys, ClockShell (tabs + floating glass chrome)
  core/designsystem/         theme (colors, typography), glass/, component/
  core/data/                 Room 3 database (db/), DataStore settings (prefs/), Hilt modules (di/)
  core/oem/                  manufacturer-specific behaviour behind OemProfile
  (core/audio, core/notifications arrive in later phases)
:core:scheduling             pure Kotlin/JVM: scheduling maths, JUnit 6 tests
```

- UI: Jetpack Compose + Material 3 with our own theme (no dynamic color). Material components
  read `MaterialTheme`, which `ClockTheme` maps from our tokens; app code reads
  `ClockTheme.colors` / `ClockTheme.typography`.
- DI: Hilt (KSP). Persistence: Room 3 (`androidx.room3`, KSP only, Android SQLite driver by
  default) with schemas exported to `app/schemas` (committed) and DataStore Preferences.
- Theme mode is applied app-wide with `UiModeManager.setApplicationNightMode` so splash, window
  and system bars follow it; Compose reads it through `isSystemInDarkTheme()`.

### Design system

- Black and white; dark mode is pure black, light mode is its direct inversion. One accent
  (red by default), reserved for action points. Presets must keep >= 3:1 contrast on black and
  white (`AccentPaletteTest`).
- Fonts: **Doto** for times only, **JetBrains Mono** for everything else (variable fonts in
  `res/font`, OFL in `licenses/fonts`). Thin icons: Material Symbols Rounded, weight 200.
- Use `sp` for text and let long strings shrink with `TextAutoSize` rather than clip.

### Liquid Glass

- Only floating layers are glass: navigation capsule, "+", sheets, menus, snooze wheel, alarm
  screen controls. List cards and main text stay solid.
- `GlassSurface` (`core/designsystem/glass`) is the only place that touches Haze; screens use
  `GlassBackdrop`, `glassBackdropSource()` and `GlassSurface`. The glass must not be inside the
  backdrop source layer.
- `GlassLevel`: `Full` (Android 13+, refraction via AGSL), `BlurOnly` (Android 12),
  `Solid` (user "Reduce transparency" or the system disabled cross-window blur, e.g. battery
  saver). Resolved by `resolveGlassLevel`.
- Debug builds have **Settings > Developer > Glass lab** to compare the levels.

### Database rule

Until the first public release, schema version 1 may be regenerated. From then on every schema
change bumps the version and ships with a migration and a test in `AppDatabaseMigrationTest`.

## Commands

The shell needs `JAVA_HOME` (Android Studio's JBR) and `platform-tools` on `PATH`; both are set in
`~/.zshrc`. Non-interactive shells may not load it, so export them explicitly if needed:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

| Task | Command |
|---|---|
| Debug APK | `./gradlew assembleDebug` |
| Install on device | `./gradlew installDebug` |
| Unit tests | `./gradlew testDebugUnitTest :core:scheduling:test` |
| Lint (warnings are errors) | `./gradlew lintDebug :core:scheduling:lint` |
| Permission guard | `./gradlew verifyDebugPermissions verifyReleasePermissions` |
| Device tests (Room migrations) | `./gradlew connectedDebugAndroidTest` |
| Everything CI runs | the four rows above (see `.github/workflows/ci.yml`) |
| Release APK | `./gradlew assembleRelease` (signed only if `keystore.properties` exists) |

Launch the debug app: `adb shell am start -n io.github.imlemos.clock.debug/io.github.imlemos.clock.MainActivity`

## Testing on the phone (every phase)

1. **Ask** the maintainer to connect the phone to the Mac by USB cable and **wait for
   confirmation**. From then on, test autonomously over adb:
   - build and install (`./gradlew installDebug`), launch (`am start`), interact
     (`adb shell input tap|swipe|text|keyevent`, `am`, `cmd`);
   - find elements by text/content description with `uiautomator dump` instead of hard-coded
     coordinates; take screenshots with `adb exec-out screencap -p` and look at them;
   - read `adb logcat` (and `-b crash`), and use `dumpsys` (`alarm`, `deviceidle`,
     `notification`, `audio`, `window`, `uimode`) to confirm behaviour;
   - languages: `adb shell cmd locale set-app-locales <pkg> --locales pt-PT|en-US`;
     theme: the app's own setting or `adb shell cmd uimode night yes|no`.
2. Alarm scenarios to automate where possible: locked screen (`input keyevent KEYCODE_POWER`),
   forced Doze (`dumpsys deviceidle force-idle`, then `unforce`), app killed (`am kill`,
   `am force-stop`), reboot (`adb reboot`, then `adb wait-for-device` and wait for
   `sys.boot_completed`), and check in `dumpsys alarm` that alarms are scheduled.
3. Only ask the maintainer for what needs a person: hearing sound, feeling vibration, unlocking
   with PIN/biometrics, accepting system dialogs adb cannot tap, judging visuals. Say exactly what
   to do and what to observe.
4. Screen staying on: `adb shell svc power stayon usb` during a session; restore with
   `adb shell svc power stayon false` at the end.
5. At the end of each test session give a short summary: what passed, what failed, what the
   maintainer must still check by hand.
6. Never leave a release build signed with a non-release key installed (it would block the real
   release from updating): uninstall it after testing.

### ColorOS notes (reference device)

- Needed: Developer options (Settings > About device > Version > tap "Build number" 7 times),
  then **USB debugging**, and accept the computer's RSA key on first connection.
- On ColorOS 16.0.10 nothing else was needed for `adb install`, `input`, `uiautomator`,
  `screencap`, `cmd locale`, `cmd power` and `connectedDebugAndroidTest`.
- `adb shell pm clear` is **blocked** by ColorOS (`CLEAR_APP_USER_DATA` SecurityException) unless
  Developer options > **"Disable permission monitoring"** is on. Without it, reset app data with
  `adb uninstall` + reinstall. Some `pm grant`/`appops` changes may hit the same restriction.
- If installs start failing, check Developer options > **"Install via USB"** (may require an
  OPPO account).
- ColorOS kills background apps aggressively: reliability work (phase 1) must be tested with the
  app removed from recents and after a reboot.
