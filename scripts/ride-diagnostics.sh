#!/usr/bin/env bash
set -euo pipefail
remote=/sdcard/Android/data/com.karoopoc/files/diagnostics
case "${1:-}" in
  enable)
    adb shell mkdir -p "$remote"
    adb shell touch "$remote/enabled"
    echo 'Ride diagnostics enabled until explicitly disabled (including after reboot).'
    ;;
  disable)
    adb shell rm -f "$remote/enabled"
    echo 'Ride diagnostics disabled. Existing logs retained.'
    ;;
  collect)
    destination="${2:-./ride-diagnostics-$(date +%Y%m%d-%H%M%S)}"
    mkdir -p "$destination"
    adb pull "$remote/." "$destination/"
    echo "Saved ride diagnostics to $destination. Read ride.previous.log before ride.log."
    ;;
  *) echo 'Usage: scripts/ride-diagnostics.sh enable|disable|collect [destination]' >&2; exit 1 ;;
esac
