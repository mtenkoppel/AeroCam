#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${1:-$PROJECT_ROOT/screenshots/aerocam-$(date +%Y%m%d-%H%M%S).png}"

if ! adb get-state >/dev/null 2>&1; then
    echo "No authorized Karoo found over ADB." >&2
    exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
adb exec-out screencap -p > "$OUTPUT"

echo "$OUTPUT"
