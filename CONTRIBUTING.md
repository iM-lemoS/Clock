<p align="right"><b>English</b> · <a href="CONTRIBUTING.pt.md">Português</a></p>

# Contributing to Clock

Thanks for helping. Clock has one job above all others, **alarms that always ring**, so the most
useful contributions are the ones that make alarms more reliable on more phones.

## Ways to help

- **Report an alarm that did not ring** (or rang late). Use the
  [bug report](https://github.com/iM-lemoS/Clock/issues/new?template=bug_report.yml) and include
  the phone model, Android version, manufacturer skin (ColorOS, One UI, HyperOS…), what the phone
  was doing (locked, battery saver, just rebooted) and what **Settings > Alarm reliability**
  shows.
- **Share how a manufacturer kills apps.** Steps that keep alarms working on a given brand go into
  `core/oem`, so every user of that brand gets them.
- **Translate.** Strings live in `app/src/main/res/values*/strings.xml`. Open an issue first so we
  can agree on the locale.
- **Code.** Pick an open issue or open one describing the change before starting anything large.

Issues and pull requests can be written in English or Portuguese.

## Ground rules

These are checked in review, and several are enforced by the build:

- **No network, no tracking.** No `INTERNET` permission, no analytics, no dependency that talks to
  the network, no storage permissions (use the system pickers and copy into app storage).
  `verify<Variant>Permissions` fails the build otherwise.
- **No text in code.** Every user-visible string goes in `values/strings.xml` (English) **and**
  `values-pt/strings.xml` (European Portuguese, addressing the user informally as "tu"). Missing
  translations fail lint.
- **Manufacturer-specific code only in `core/oem`**, behind `OemProfile`, with a generic fallback.
- **Scheduling maths is pure Kotlin** in `:core:scheduling` (no Android dependencies) and comes
  with unit tests: next occurrence, recurrences, time zones and DST are where a bug means someone
  does not wake up.
- **Database changes**: once a release is out, every schema change bumps the Room version and ships
  with a migration and a test in `AppDatabaseMigrationTest`.
- **Versions** live only in `gradle/libs.versions.toml`; use the latest stable releases.
- **English** for code, comments, identifiers, commit messages and technical docs.
  `docs/PLANO.md` (the product plan) stays in Portuguese.

## Getting started

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.

| Task | Command |
|---|---|
| Debug APK | `./gradlew assembleDebug` |
| Install on a device | `./gradlew installDebug` |
| Unit tests | `./gradlew testDebugUnitTest :core:scheduling:test` |
| Lint (warnings are errors) | `./gradlew lintDebug :core:scheduling:lint` |
| Permission guard | `./gradlew verifyDebugPermissions verifyReleasePermissions` |
| Device tests (Room migrations) | `./gradlew connectedDebugAndroidTest` |

CI runs the build, lint, unit tests and the permission guard on every push and pull request.

Debug builds install next to a release build (`io.github.imlemos.clock.debug`) and include adb
hooks for testing alarms on a locked phone:

```sh
C=io.github.imlemos.clock.debug/io.github.imlemos.clock.debug.DebugCommandsReceiver
adb shell am broadcast -a io.github.imlemos.clock.debug.TEST_ALARM -n $C --ei in_minutes 1 --es label "'Test'"
adb shell am broadcast -a io.github.imlemos.clock.debug.START_TIMER -n $C --ei seconds 10 --es name Tea
```

## Architecture in one minute

```
:app                         Android app, organised by feature
  alarm/ timer/ stopwatch/   feature screens
  worldclock/                world clock tab
  reliability/               alarm reliability screen (permissions, battery, manufacturer steps)
  trash/                     deleted alarms, restorable for 30 days
  alarm/layout/              alarm screen layouts: model, geometry, presets, renderer, editor
  settings/ navigation/      settings screens; Navigation 3 keys and the tab shell
  core/designsystem/         theme, glass surfaces, shared components, haptics, motion
  core/data/                 Room database, DataStore settings, Hilt modules
  core/audio/                alarm sound and volume
  core/notifications/        notification channels and builders
  core/time/                 elapsed-time clock and reboot detection
  core/oem/                  manufacturer-specific behaviour behind OemProfile
  core/zones/                localised time zone names and the zone picker
:core:scheduling             pure Kotlin: scheduling maths with JUnit tests
```

UI is Jetpack Compose with our own Material 3 theme. Only floating layers (navigation, sheets,
dialogs, alarm screen controls) use the glass surface; `GlassSurface` is the only code that touches
the blur library.

## Testing alarms on a phone

Changes to scheduling, ringing or notifications should be tried on a real device. Useful
scenarios: locked screen, forced Doze (`adb shell dumpsys deviceidle force-idle`), app killed
(`adb shell am kill`), reboot, and before the first unlock after a reboot. Confirm the alarm is
scheduled with `adb shell dumpsys alarm | grep imlemos`. Mention the device and what you tested in
the pull request.

## Pull requests

- Keep them small and focused; one change per commit, with a descriptive message.
- Run the unit tests, lint and the permission guard before pushing.
- Add or update tests for scheduling changes.
- Add strings in both languages and include screenshots for UI changes.
- By contributing you agree that your work is published under the project's licence.
