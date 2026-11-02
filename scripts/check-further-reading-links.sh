#!/bin/sh
# Manual link-health check for the further-reading map (#810).
#
# NOT part of the build and never run by testDebugUnitTest: unit tests stay
# network-free. Run this by hand when a link is added or edited — it extracts
# every URL from GrammarFurtherReading.kt and reports each HTTP status.
set -eu
cd "$(dirname "$0")/.."

file="app/src/main/java/app/olauncher/data/store/GrammarFurtherReading.kt"
urls=$(grep -o 'https://[^"]*' "$file" | sort -u)

if [ -z "$urls" ]; then
    echo "No URLs found in $file" >&2
    exit 1
fi

fail=0
for url in $urls; do
    code=$(curl -s -o /dev/null -w "%{http_code}" -L --max-time 20 "$url")
    case "$code" in
        2*) echo "OK   $code  $url" ;;
        *)  echo "BAD  $code  $url"; fail=1 ;;
    esac
done

if [ "$fail" -ne 0 ]; then
    echo "At least one further-reading link is unhealthy."
    exit 1
fi
echo "All further-reading links responded 2xx."
