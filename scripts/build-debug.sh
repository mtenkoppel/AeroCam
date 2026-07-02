#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/karoo-env.sh"

exec "$PROJECT_ROOT/gradlew" :app:assembleDebug "$@"
