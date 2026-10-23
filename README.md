# Clock

An open-source Android clock app (alarms, world clock, timers, stopwatch) built around one
promise: **alarms that always ring**, with fine-grained control over each one.

- Works on any Android 12+ device, with per-manufacturer reliability guidance (ColorOS, One UI,
  HyperOS and more) and a warning when a system setting may stop alarms.
- Private by design: no internet permission, no analytics, no accounts. Everything stays on the
  device.
- English and European Portuguese; follows the phone's language.
- Black-and-white design with a single accent colour, dot-matrix digits and "liquid glass"
  floating controls.

## Features

- **Alarms**: once, weekly or on chosen dates; single use; skip next; groups with a master
  switch; multi-select and batch edit; collision warnings; trash with undo.
- **Sound**: any system tone or file, gradual volume, vibration patterns (or synced to the sound),
  ring duration, phone + Bluetooth output, volume-down to silence.
- **Alarm screen**: snooze with a tap, or hold and slide over a dial to pick 5 min to 1 h; slide
  to dismiss; editable layouts (clock styles and fonts, colours, photo, video or animated
  backgrounds) in a library; works over the lock screen and before the first unlock.
- **Time zones**: per-alarm zone, a manual app zone, both times shown.
- **World clock**, **timers** (several at once, live notifications, full screen when they end) and
  a **stopwatch** with laps.

> Status: feature-complete, in validation before the first release.
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

- Fonts, all under the SIL Open Font License 1.1 (`licenses/fonts`), bundled unmodified:
  [Doto](https://github.com/oliverlalan/Doto), [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono),
  and for alarm screen clocks [Outfit](https://github.com/Outfitio/Outfit-Fonts),
  [Playfair Display](https://github.com/clauseggers/Playfair-Display),
  [Orbitron](https://github.com/theleagueof/orbitron),
  [Big Shoulders Display](https://github.com/xotypeco/big_shoulders) and
  [Comfortaa](https://github.com/alexeiva/comfortaa).
- [Material Symbols](https://github.com/google/material-design-icons): Apache License 2.0
  (`licenses/icons`).

## License

To be decided before the first public release.
