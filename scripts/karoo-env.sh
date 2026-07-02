#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-"$PROJECT_ROOT/.gradle"}"
# Keep the standard Android debug key stable across Android Studio and CLI builds.
# Changing ANDROID_USER_HOME creates a different debug.keystore, and Android then
# refuses to update an existing installation without clearing its app data.
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-"$HOME/.android"}"

if [[ -z "${JAVA_HOME:-}" && -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
