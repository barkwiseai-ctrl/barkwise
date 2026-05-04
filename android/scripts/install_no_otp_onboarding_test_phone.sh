#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
APK_SOURCE="$ANDROID_DIR/app/build/outputs/apk/staging/debug/app-staging-debug.apk"
PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app.staging}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.petsocial.app.MainActivity}"
PROFILE_MODE="${PROFILE_MODE:-onboarding}"
SKIP_BUILD="${SKIP_BUILD:-0}"
CLEAR_DATA="${CLEAR_DATA:-1}"
SERIAL="${SERIAL:-}"
BARKWISE_STAGING_API_BASE_URL="${BARKWISE_STAGING_API_BASE_URL:-https://api.barkwiseai.com/}"

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "Missing required command: $cmd" >&2
    exit 1
  }
}

case "$PROFILE_MODE" in
  ready|onboarding)
    ;;
  *)
    echo "PROFILE_MODE must be ready or onboarding" >&2
    exit 1
    ;;
esac

require_cmd adb

ADB_ARGS=()
if [[ -n "$SERIAL" ]]; then
  ADB_ARGS=(-s "$SERIAL")
else
  ADB_ARGS=(-d)
fi

echo "Checking for connected physical Android device..."
adb "${ADB_ARGS[@]}" get-state >/dev/null

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "Building BarkWise Test with mock data, OTP disabled, and API base ${BARKWISE_STAGING_API_BASE_URL}..."
  (
    cd "$ANDROID_DIR"
    BARKWISE_STAGING_API_BASE_URL="$BARKWISE_STAGING_API_BASE_URL" \
      BARKWISE_TEST_USE_MOCK_DATA=true \
      BARKWISE_TEST_ALLOW_DEMO_LOGIN=true \
      BARKWISE_TEST_REQUIRE_INVITE_OTP_AUTH=false \
      ./gradlew :app:assembleStagingDebug
  )
fi

if [[ ! -f "$APK_SOURCE" ]]; then
  echo "Missing APK at $APK_SOURCE" >&2
  exit 1
fi

echo "Installing $PACKAGE_NAME..."
adb "${ADB_ARGS[@]}" install -r "$APK_SOURCE" >/dev/null

if [[ "$CLEAR_DATA" == "1" ]]; then
  echo "Clearing app data for a fresh no-OTP onboarding run..."
  adb "${ADB_ARGS[@]}" shell pm clear "$PACKAGE_NAME" >/dev/null
fi

DEEP_LINK_URI="barkwise://join?profile_mode=${PROFILE_MODE}"
echo "Launching $PACKAGE_NAME with profile_mode=$PROFILE_MODE..."
adb "${ADB_ARGS[@]}" shell am start -W -a android.intent.action.VIEW -d "$DEEP_LINK_URI" "${PACKAGE_NAME}/${ACTIVITY_NAME}" >/dev/null

cat <<EOF
Done.
Expected state:
  - App: BarkWise Test ($PACKAGE_NAME)
  - Auth: OTP disabled for this test build
  - Data: mock data enabled
  - API: $BARKWISE_STAGING_API_BASE_URL
  - Startup: profile_mode=$PROFILE_MODE

To rerun:
  ./android/scripts/install_no_otp_onboarding_test_phone.sh
EOF
