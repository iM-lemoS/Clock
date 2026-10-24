# Releasing

Maintainer notes for publishing a signed build.

## Signing

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

## Checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the "Unreleased" entries in `CHANGELOG.md` under the new version, and write
   `docs/releases/vX.Y.Z.md` plus `fastlane/metadata/android/{en-US,pt-PT}/changelogs/<versionCode>.txt`
   (500 characters at most).
3. From the first public release on, any Room schema change needs a migration and a test in
   `AppDatabaseMigrationTest`.
4. Run everything CI runs, plus `./gradlew connectedDebugAndroidTest` on a device.
5. `./gradlew assembleRelease`, then check the signature:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`.
   The SHA-256 must match the one in the release notes.
6. Install it on a phone, test an alarm on the locked screen, then **uninstall it** if it was signed
   with anything other than the release key.
7. Tag (`git tag -a vX.Y.Z -m "Clock X.Y.Z"`), push the tag, and create the GitHub release with
   the notes and the APK renamed to `Clock-X.Y.Z.apk`:

   ```sh
   gh release create vX.Y.Z Clock-X.Y.Z.apk --title "Clock X.Y.Z" --notes-file docs/releases/vX.Y.Z.md
   ```
