#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
RAILWAY_BASE_URL="${RAILWAY_BASE_URL:-https://barkwise-production.up.railway.app/}"
PACKAGE_NAME="${PACKAGE_NAME:-com.petsocial.app.staging}"

echo "Installing staging build against Railway: ${RAILWAY_BASE_URL}"
(
  cd "$ANDROID_DIR"
  BARKWISE_STAGING_API_BASE_URL="$RAILWAY_BASE_URL" ./gradlew :app:installStagingDebug
)

echo "Launching ${PACKAGE_NAME} on physical device..."
adb -d shell am start -n "${PACKAGE_NAME}/com.petsocial.app.MainActivity" >/dev/null

echo "Done."
