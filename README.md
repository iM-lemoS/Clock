# Clock

An open-source Android clock app (alarms, timers, stopwatch) built around one promise: **alarms
that always ring**, with fine-grained control over each one.

- Works on any Android 12+ device, with optional per-manufacturer reliability guidance.
- Private by design: no internet permission, no analytics, no accounts. Everything stays on the
  device.
- English and European Portuguese.
- Black-and-white design with a single accent color, dot-matrix time digits and "liquid glass"
  floating controls.

> Status: early development (phase 0, project foundation). Not ready for daily use yet.
> The product plan lives in [`docs/PLANO.md`](docs/PLANO.md) (Portuguese).

## Building

Requirements: JDK 17 or newer (Android Studio's bundled JBR works) and the Android SDK with
platform 37.

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on a connected device
./gradlew testDebugUnitTest :core:scheduling:test lintDebug
```

## Release signing

Release builds are signed only when a `keystore.properties` file exists at the repository root.
That file and every keystore are git-ignored; never commit them.

1. Create the keystore **outside** the repository, once:

   ```sh
   mkdir -p ~/Keys/clock
   keytool -genkeypair -v -keystore ~/Keys/clock/clock-release.jks \
     -alias clock -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in the absolute path and
   passwords.
3. `./gradlew assembleRelease` now produces a signed `app-release.apk`.

Always sign with the same key: Android refuses updates signed with a different key, and users
would have to uninstall (losing their alarms). Keep a backup of the keystore and its passwords in
a password manager.

## Project layout

- `app/`: the Android app (Compose, Hilt, Room, DataStore), organised by feature.
- `core/scheduling/`: pure Kotlin scheduling logic with unit tests.
- `licenses/`: licences of bundled fonts and icons.

## Third-party assets

- [Doto](https://github.com/oliverlalan/Doto) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono):
  SIL Open Font License 1.1 (`licenses/fonts`).
- [Material Symbols](https://github.com/google/material-design-icons): Apache License 2.0
  (`licenses/icons`).

## License

To be decided before the first public release.
