#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
LOCAL_BASE_URL="${LOCAL_BASE_URL:-http://127.0.0.1:8000/}"
PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app.staging}"

echo "Ensuring staging local routing watchdog is running..."
"$ANDROID_DIR/scripts/start_staging_local_routing.sh"

echo "Installing staging build against local API: ${LOCAL_BASE_URL}"
(
  cd "$ANDROID_DIR"
  BARKWISE_STAGING_API_BASE_URL="$LOCAL_BASE_URL" ./gradlew :app:installStagingDebug
)

echo "Launching ${PACKAGE_NAME} on physical device..."
adb -d shell am start -W -n "${PACKAGE_NAME}/com.petsocial.app.MainActivity" >/dev/null

echo "Current adb reverse mappings:"
adb -d reverse --list

echo "Done."
