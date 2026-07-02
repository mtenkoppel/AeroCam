#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/karoo-env.sh"

ADB="${ANDROID_HOME:-}/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  ADB="adb"
fi

exec "$ADB" logcat -s KarooPoC
