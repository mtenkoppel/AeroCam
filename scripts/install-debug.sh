#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/karoo-env.sh"

"$PROJECT_ROOT/gradlew" :app:installDebug "$@"
"$PROJECT_ROOT/scripts/launch-app.sh"
