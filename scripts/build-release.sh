#!/usr/bin/env bash
# Build the release APK the same way every time and, with --publish, create the GitHub release.
#
# Usage:
#   scripts/build-release.sh [--number <n>] [--publish]
#
#   --number   build number to use (default: last build-<n> release tag + 1)
#   --publish  create release build-<n> with thumbline.apk (otherwise only builds)
set -euo pipefail

cd "$(dirname "$0")/.."

readonly WORKFLOW=".github/workflows/build-apk.yml"
readonly KEYSTORE="app/debug.keystore"
readonly APK_DIR="app/build/outputs/apk/release"
readonly OUT_APK="thumbline.apk"

number=""
publish=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --number) number="$2"; shift 2 ;;
    --publish) publish=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

trap 'rm -f "$OUT_APK"' EXIT

# 1. Keystore must match the checksum recorded in the workflow, so every build is signed by the same key.
expected=$(grep -o 'EXPECTED="[0-9a-f]*"' "$WORKFLOW" | cut -d'"' -f2)
actual=$(shasum -a 256 "$KEYSTORE" | awk '{print $1}')
if [[ "$actual" != "$expected" ]]; then
  echo "Keystore checksum mismatch for $KEYSTORE" >&2
  exit 1
fi

# 2. Pick the build number: next after the newest build-<n> release, unless given.
if [[ -z "$number" ]]; then
  last=$(gh release list --limit 100 --json tagName --jq '.[].tagName' | sed -n 's/^build-\([0-9]*\)$/\1/p' | sort -n | tail -1)
  number=$(( ${last:-0} + 1 ))
fi
echo "Build number: $number (version code $(( 113 + number )))"

# 3. Build and run unit tests.
export JAVA_HOME
JAVA_HOME=$(/usr/libexec/java_home -v 17)
GITHUB_RUN_NUMBER="$number" ./gradlew assembleRelease testDebugUnitTest --no-daemon

apk=$(ls "$APK_DIR"/*.apk | head -1)
cp "$apk" "$OUT_APK"

# 4. Check the signature and package when the Android build tools are installed.
build_tools=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)
if [[ -n "$build_tools" ]]; then
  "$build_tools/apksigner" verify --print-certs "$OUT_APK" | grep 'certificate SHA-256'
  "$build_tools/aapt2" dump badging "$OUT_APK" | grep -E '^package:'
fi

# 5. Publish only when asked.
if [[ "$publish" == true ]]; then
  gh release create "build-$number" "$OUT_APK" --target main --title "build-$number" --generate-notes --latest
  echo "Published build-$number"
else
  echo "Built $OUT_APK (not published; pass --publish to release it)"
fi
