#!/usr/bin/env bash
set -euo pipefail

MODE_RAW="${1:-}"
if [[ -z "${MODE_RAW}" ]]; then
  cat <<'EOF'
Usage:
  android/scripts/set_profile_mode.sh <ready|onboarding>

Optional env vars:
  PACKAGE_NAME (default: com.barkwise.app.staging)
  ACTIVITY_NAME (default: com.petsocial.app.MainActivity)
EOF
  exit 1
fi

MODE="$(echo "${MODE_RAW}" | tr '[:upper:]' '[:lower:]')"
case "${MODE}" in
  ready|onboarding)
    ;;
  *)
    echo "Invalid mode: ${MODE_RAW}. Use 'ready' or 'onboarding'."
    exit 1
    ;;
esac

PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app.staging}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.petsocial.app.MainActivity}"
DEEP_LINK_URI="barkwise://join?profile_mode=${MODE}"

echo "Checking for connected physical Android device..."
adb -d get-state >/dev/null

echo "Applying profile mode '${MODE}' to ${PACKAGE_NAME}..."
adb -d shell am start -W -a android.intent.action.VIEW -d "${DEEP_LINK_URI}" "${PACKAGE_NAME}/${ACTIVITY_NAME}" >/dev/null

echo "Done. Mode is now '${MODE}' for ${PACKAGE_NAME}."
