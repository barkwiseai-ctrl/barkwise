#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"

OWNER_SERIAL="${OWNER_SERIAL:-}"
PROVIDER_SERIAL="${PROVIDER_SERIAL:-}"
LOCAL_BASE_URL="${LOCAL_BASE_URL:-http://127.0.0.1:8000/}"
OWNER_USER_ID="${OWNER_USER_ID:-user_2}"
PROVIDER_USER_ID="${PROVIDER_USER_ID:-user_1}"

if [[ -z "$OWNER_SERIAL" || -z "$PROVIDER_SERIAL" ]]; then
  cat <<'EOF' >&2
Set both OWNER_SERIAL and PROVIDER_SERIAL.

Example:
  OWNER_SERIAL=R5GL12PJLCM \
  PROVIDER_SERIAL=R5CW928G37B \
  ./android/scripts/bootstrap_dual_phone_local_mvp.sh
EOF
  exit 1
fi

echo "Ensuring local routing watchdog is running..."
"$ANDROID_DIR/scripts/start_staging_local_routing.sh"

echo "Installing owner staging on ${OWNER_SERIAL}..."
(
  cd "$ANDROID_DIR"
  ANDROID_SERIAL="$OWNER_SERIAL" \
  BARKWISE_STAGING_API_BASE_URL="$LOCAL_BASE_URL" \
    ./gradlew :app:installStagingDebug
)

echo "Installing provider staging on ${PROVIDER_SERIAL}..."
(
  cd "$ANDROID_DIR"
  ANDROID_SERIAL="$PROVIDER_SERIAL" \
  BARKWISE_PROVIDER_STAGING_API_BASE_URL="$LOCAL_BASE_URL" \
    ./gradlew :app:installProviderStagingDebug
)

echo "Seeding owner auth session..."
"$ANDROID_DIR/scripts/seed_phone_auth_session.sh" \
  --serial "$OWNER_SERIAL" \
  --package com.barkwise.app.staging \
  --user-id "$OWNER_USER_ID" \
  --api-base-url "$LOCAL_BASE_URL" \
  --profile-mode ready \
  --launch

echo "Seeding provider auth session..."
"$ANDROID_DIR/scripts/seed_phone_auth_session.sh" \
  --serial "$PROVIDER_SERIAL" \
  --package com.barkwise.app.provider.staging \
  --user-id "$PROVIDER_USER_ID" \
  --api-base-url "$LOCAL_BASE_URL" \
  --profile-mode ready \
  --launch

echo "Current devices:"
adb devices -l

echo "Owner focus:"
adb -s "$OWNER_SERIAL" shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' || true

echo "Provider focus:"
adb -s "$PROVIDER_SERIAL" shell dumpsys window | rg 'mCurrentFocus|mFocusedApp' || true

echo "Done."
