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

## Build pipeline (on demand only)

- The GitHub Actions workflow `build-apk.yml` runs only when triggered by hand (`workflow_dispatch`): Actions tab, or `gh workflow run build-apk.yml --ref main`. It does not run on push or PR.
- Trigger it, or do the manual release below, after every set of testable features is delivered (for example at the end of an epic group), not after every PR.
- **GitHub Actions is down until end of October 2026** ($0 budget). Until then, publish releases by hand:
  1. `n` = the last `build-<n>` tag (`gh release list --limit 1`) + 1.
  2. Check the keystore: `shasum -a 256 app/debug.keystore` must match the checksum in `.github/workflows/build-apk.yml`.
  3. `JAVA_HOME=$(/usr/libexec/java_home -v 17) GITHUB_RUN_NUMBER=<n> ./gradlew assembleDebug testDebugUnitTest --no-daemon`
  4. Copy `app/build/outputs/apk/debug/*.apk` to `thumbline.apk` (check the exact filename first).
  5. `gh release create build-<n> thumbline.apk --target main --title build-<n> --generate-notes --latest`. `--target` takes a branch name, not a SHA. Use the local `gh` login; never print its token.
  6. Remove the copied `thumbline.apk` afterwards.

## Standing rules for every agy task

- Never regenerate or modify `app/debug.keystore`. Never change `applicationId`, `applicationIdSuffix` or `namespace`.
- Never rename or remove existing SharedPreferences keys, or the prefs file name `app.olauncher`. Add new keys only.
- Keep the GPLv3 licence and the Olauncher credit.
- No analytics, no new third-party runtime dependencies without the epic saying so.
- Runtime permissions are requested only when the feature is first switched on, with a one-line explanation. A denial must leave the app working.
- New user-facing strings go in `app/src/main/res/values/strings.xml` only (other locales fall back to English).
- Put logic in Context-free Kotlin where possible and cover it with JUnit tests in `app/src/test/java/app/olauncher/`.
- Match the surrounding code style (Kotlin, ViewBinding, `Prefs` property pattern, `showMessageDialog` for dialogs).
