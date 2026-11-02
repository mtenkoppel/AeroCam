#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 /path/to/app-release.apk" >&2
  exit 1
fi
adb get-state >/dev/null
# A signature mismatch must fail; never uninstall or clear data as a fallback.
adb install -r "$1"
adb shell am start -W -n com.karoopoc/.MainActivity
