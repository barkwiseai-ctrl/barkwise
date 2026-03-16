#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app.staging}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.petsocial.app.MainActivity}"
AUTO_INSTALL="${AUTO_INSTALL:-0}"

echo "Checking for connected physical Android device..."
adb -d get-state >/dev/null

if ! adb -d shell pm list packages "$PACKAGE_NAME" | rg -q "$PACKAGE_NAME"; then
  if [[ "$AUTO_INSTALL" == "1" ]]; then
    echo "Package ${PACKAGE_NAME} not installed. Installing staging debug..."
    (
      cd "$ANDROID_DIR"
      ./gradlew :app:installStagingDebug
    )
  else
    cat <<EOF
Package ${PACKAGE_NAME} is not installed on the connected device.
Install first with:
  cd ${ANDROID_DIR}
  ./gradlew :app:installStagingDebug
Or rerun with AUTO_INSTALL=1.
EOF
    exit 1
  fi
fi

echo "Force-stopping ${PACKAGE_NAME}..."
adb -d shell am force-stop "$PACKAGE_NAME" || true

echo "Clearing app data for ${PACKAGE_NAME}..."
adb -d shell pm clear "$PACKAGE_NAME" >/dev/null

echo "Launching ${PACKAGE_NAME}..."
adb -d shell am start -W -n "${PACKAGE_NAME}/${ACTIVITY_NAME}" >/dev/null

echo "Reset complete."
echo "Expected state: BarkWise Test opens cleanly for onboarding-mode checks."
