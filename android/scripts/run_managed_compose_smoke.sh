#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
DEVICE_NAME="${MANAGED_DEVICE_NAME:-pixel8Api35Atd}"
TASK_NAME="${MANAGED_COMPOSE_TASK:-:app:managedComposeSmoke}"

step() {
  echo
  echo "==> $1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

if [[ -z "$SDK_ROOT" ]]; then
  fail "ANDROID_SDK_ROOT or ANDROID_HOME must point to an Android SDK with emulator support."
fi

[[ -d "$SDK_ROOT" ]] || fail "Android SDK directory not found: $SDK_ROOT"
[[ -x "$SDK_ROOT/emulator/emulator" ]] || fail "Android emulator binary missing at $SDK_ROOT/emulator/emulator"

step "Managed Compose smoke tests"
echo "Using SDK: $SDK_ROOT"
echo "Running task: $TASK_NAME"

(
  cd "$ANDROID_DIR"
  ./gradlew "$TASK_NAME"
)
