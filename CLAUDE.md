# Thumbline

Thumbline is a minimalist Android launcher forked from Olauncher (licensed under GPLv3), featuring bottom-aligned home apps, quick icon shortcuts, and clean on-device customization.

## Build & Test Commands

- **Build debug APK**: `./gradlew assembleDebug`
- **Run unit tests**: `./gradlew testDebugUnitTest`
- **Full verification**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug testDebugUnitTest --no-daemon`
- **Note**: Run build and JVM unit tests by default. Instrumented or emulator tests (Android Studio emulator, `connectedAndroidTest`) are allowed when the situation demands it, for example to check a bug that JVM tests cannot reproduce. The agent decides whether to run them.

## Release Configuration

- Releases must ship the **debug** variant (`app.thumbline.debug` is the installed package).
- Releases are signed with the committed `app/debug.keystore` so that every CI build has consistent signing and can update in place (e.g., via Obtainium).
- **Version code** is `113 + GITHUB_RUN_NUMBER` (see `app/build.gradle*`). Local builds use `0`, so they are older than any CI build and `adb install` fails with `INSTALL_FAILED_VERSION_DOWNGRADE`. To install a local build over a CI build on a device, build with the same number the device runs: `GITHUB_RUN_NUMBER=<n> ./gradlew assembleDebug` (device version code = 113 + n). Check the device with `adb shell dumpsys package app.thumbline.debug | grep versionCode`. `install -d` does not bypass this for this app.

## Standing rules for every agy task

- Never regenerate or modify `app/debug.keystore`. Never change `applicationId`, `applicationIdSuffix` or `namespace`.
- Never rename or remove existing SharedPreferences keys, or the prefs file name `app.olauncher`. Add new keys only.
- Keep the GPLv3 licence and the Olauncher credit.
- No analytics, no new third-party runtime dependencies without the epic saying so.
- Runtime permissions are requested only when the feature is first switched on, with a one-line explanation. A denial must leave the app working.
- New user-facing strings go in `app/src/main/res/values/strings.xml` only (other locales fall back to English).
- Put logic in Context-free Kotlin where possible and cover it with JUnit tests in `app/src/test/java/app/olauncher/`.
- Match the surrounding code style (Kotlin, ViewBinding, `Prefs` property pattern, `showMessageDialog` for dialogs).
