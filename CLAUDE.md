# Thumbline

Thumbline is a minimalist Android launcher forked from Olauncher (licensed under GPLv3), featuring bottom-aligned home apps, quick icon shortcuts, and clean on-device customization.

## Build & Test Commands

- **Build debug APK**: `./gradlew assembleDebug`
- **Build release APK + publish**: `scripts/build-release.sh` (add `--publish` to create the GitHub release; without it, only builds `thumbline.apk`)
- **Run unit tests**: `./gradlew testDebugUnitTest`
- **Full verification**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug testDebugUnitTest --no-daemon`
- **Note**: Run build and JVM unit tests by default. Instrumented or emulator tests (Android Studio emulator, `connectedAndroidTest`) are allowed when the situation demands it, for example to check a bug that JVM tests cannot reproduce. The agent decides whether to run them.

## Release Configuration

- Releases ship the **release** variant (`app.thumbline` is the installed package). It is R8-minified and signed with the committed `app/debug.keystore`, so every build updates the previous one in place (e.g., via Obtainium).
- The `debug` variant (`app.thumbline.debug`) and `dev` variant remain for local development only; they are not released.
- **Version code** is `113 + GITHUB_RUN_NUMBER` (see `app/build.gradle*`). Releases keep one rising line of version codes, so never reset or lower it. Local builds use `0`, so they are older than any release and `adb install` fails with `INSTALL_FAILED_VERSION_DOWNGRADE`. To install a local build over a release on a device, build with the same number the device runs: `GITHUB_RUN_NUMBER=<n> ./gradlew assembleRelease` (device version code = 113 + n). Check the device with `adb shell dumpsys package app.thumbline | grep versionCode`. `install -d` does not bypass this for this app.

## Build pipeline (on demand only)

- The GitHub Actions workflow `build-apk.yml` runs only when triggered by hand (`workflow_dispatch`): Actions tab, or `gh workflow run build-apk.yml --ref main`. It does not run on push or PR.
- Trigger it, or do the manual release below, after every set of testable features is delivered (for example at the end of an epic group), not after every PR.
- **GitHub Actions is down until end of October 2026** ($0 budget). Until then, publish releases by hand with `scripts/build-release.sh --publish`. The script:
  1. Checks the keystore checksum against `.github/workflows/build-apk.yml`.
  2. Uses `n` = the last `build-<n>` tag + 1 (override with `--number <n>`).
  3. Runs `assembleRelease testDebugUnitTest` with `GITHUB_RUN_NUMBER=<n>` and Java 17.
  4. Copies the APK to `thumbline.apk`, prints the signing certificate and package, then creates `build-<n>` with `gh` (`--target main`, `--latest`). Use the local `gh` login; never print its token.
  5. Removes `thumbline.apk` on exit.

## Standing rules for every agy task

- Never regenerate or modify `app/debug.keystore`. Never change `applicationId`, `applicationIdSuffix` or `namespace`.
- Never rename or remove existing SharedPreferences keys, or the prefs file name `app.olauncher`. Add new keys only.
- Keep the GPLv3 licence and the Olauncher credit.
- No analytics, no new third-party runtime dependencies without the epic saying so.
- Runtime permissions are requested only when the feature is first switched on, with a one-line explanation. A denial must leave the app working.
- New user-facing strings go in `app/src/main/res/values/strings.xml` only (other locales fall back to English).
- Put logic in Context-free Kotlin where possible and cover it with JUnit tests in `app/src/test/java/app/olauncher/`.
- Match the surrounding code style (Kotlin, ViewBinding, `Prefs` property pattern, `showMessageDialog` for dialogs).
