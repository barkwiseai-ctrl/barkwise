#!/usr/bin/env bash
set -euo pipefail

MODE_RAW="${1:-}"
if [[ -z "${MODE_RAW}" ]]; then
  cat <<'EOF'
Usage:
  android/scripts/set_profile_header_mode.sh <show|hide>

Optional env vars:
  PACKAGE_NAME (default: com.barkwise.app.staging)
  ACTIVITY_NAME (default: com.petsocial.app.MainActivity)
EOF
  exit 1
fi

MODE="$(echo "${MODE_RAW}" | tr '[:upper:]' '[:lower:]')"
case "${MODE}" in
  show|visible)
    QUERY_MODE="visible"
    ;;
  hide|hidden)
    QUERY_MODE="hidden"
    ;;
  *)
    echo "Invalid mode: ${MODE_RAW}. Use 'show' or 'hide'."
    exit 1
    ;;
esac

PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app.staging}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.petsocial.app.MainActivity}"
DEEP_LINK_URI="barkwise://join?profile_header_mode=${QUERY_MODE}"

echo "Checking for connected physical Android device..."
adb -d get-state >/dev/null

echo "Applying profile header mode '${QUERY_MODE}' to ${PACKAGE_NAME}..."
adb -d shell am start -W -a android.intent.action.VIEW -d "${DEEP_LINK_URI}" "${PACKAGE_NAME}/${ACTIVITY_NAME}" >/dev/null

echo "Done. Profile header mode is now '${QUERY_MODE}' for ${PACKAGE_NAME}."
