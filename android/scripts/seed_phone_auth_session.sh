#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./android/scripts/seed_phone_auth_session.sh \
    --serial SERIAL \
    --package PACKAGE_NAME \
    --user-id USER_ID \
    [--api-base-url URL] \
    [--password PASSWORD] \
    [--profile-mode ready|onboarding] \
    [--launch]

Example:
  ./android/scripts/seed_phone_auth_session.sh \
    --serial R5GL12PJLCM \
    --package com.barkwise.app.staging \
    --user-id user_2 \
    --api-base-url http://127.0.0.1:8000/ \
    --launch
EOF
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "Missing required command: $cmd" >&2
    exit 1
  }
}

SERIAL=""
PACKAGE_NAME=""
USER_ID=""
API_BASE_URL="http://127.0.0.1:8000/"
PASSWORD="petsocial-demo"
PROFILE_MODE="ready"
LAUNCH_AFTER_SEED=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --package)
      PACKAGE_NAME="${2:-}"
      shift 2
      ;;
    --user-id)
      USER_ID="${2:-}"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
      shift 2
      ;;
    --profile-mode)
      PROFILE_MODE="${2:-}"
      shift 2
      ;;
    --launch)
      LAUNCH_AFTER_SEED=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

[[ -n "$SERIAL" ]] || { echo "--serial is required" >&2; usage; exit 1; }
[[ -n "$PACKAGE_NAME" ]] || { echo "--package is required" >&2; usage; exit 1; }
[[ -n "$USER_ID" ]] || { echo "--user-id is required" >&2; usage; exit 1; }

case "$PROFILE_MODE" in
  ready|onboarding)
    ;;
  *)
    echo "--profile-mode must be ready or onboarding" >&2
    exit 1
    ;;
esac

require_cmd adb
require_cmd curl
require_cmd python3

API_BASE_URL="${API_BASE_URL%/}/"
LOGIN_JSON="$(curl -fsS -X POST "${API_BASE_URL}auth/login" \
  -H 'content-type: application/json' \
  -d "{\"user_id\":\"${USER_ID}\",\"password\":\"${PASSWORD}\"}")"

AUTH_TOKEN="$(printf '%s' "$LOGIN_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"
if [[ -z "$AUTH_TOKEN" ]]; then
  echo "Failed to resolve access token for ${USER_ID}" >&2
  exit 1
fi

adb -s "$SERIAL" shell pm path "$PACKAGE_NAME" >/dev/null

PREFS_PATH="shared_prefs/petsocial_cache.xml"
if ! adb -s "$SERIAL" shell run-as "$PACKAGE_NAME" ls "$PREFS_PATH" >/dev/null 2>&1; then
  echo "Expected prefs file missing for ${PACKAGE_NAME}: ${PREFS_PATH}" >&2
  exit 1
fi

TMP_PREFS_IN="$(mktemp)"
TMP_PREFS_OUT="$(mktemp)"
REMOTE_TMP="/sdcard/${PACKAGE_NAME##*.}_petsocial_cache_seed.xml"
trap 'rm -f "$TMP_PREFS_IN" "$TMP_PREFS_OUT"' EXIT

adb -s "$SERIAL" exec-out run-as "$PACKAGE_NAME" cat "$PREFS_PATH" > "$TMP_PREFS_IN"

python3 - "$TMP_PREFS_IN" "$TMP_PREFS_OUT" "$USER_ID" "$AUTH_TOKEN" "$PROFILE_MODE" <<'PY'
import sys
import xml.etree.ElementTree as ET

in_path, out_path, user_id, auth_token, profile_mode = sys.argv[1:]

tree = ET.parse(in_path)
root = tree.getroot()

def upsert_string(name: str, value: str) -> None:
    for child in root.findall("string"):
        if child.attrib.get("name") == name:
            child.text = value
            return
    node = ET.Element("string", {"name": name})
    node.text = value
    root.append(node)

upsert_string("active_user_id", user_id)
upsert_string("auth_token", auth_token)
upsert_string("test_profile_mode", profile_mode)

tree.write(out_path, encoding="utf-8", xml_declaration=True)
PY

adb -s "$SERIAL" push "$TMP_PREFS_OUT" "$REMOTE_TMP" >/dev/null
adb -s "$SERIAL" shell "cat '$REMOTE_TMP' | run-as '$PACKAGE_NAME' dd of='$PREFS_PATH' >/dev/null"
adb -s "$SERIAL" shell "rm '$REMOTE_TMP' >/dev/null 2>&1 || true"

if [[ "$API_BASE_URL" == "http://127.0.0.1:8000/" || "$API_BASE_URL" == "http://localhost:8000/" ]]; then
  adb -s "$SERIAL" reverse tcp:8000 tcp:8000 >/dev/null
fi

adb -s "$SERIAL" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb -s "$SERIAL" shell pm grant "$PACKAGE_NAME" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
adb -s "$SERIAL" shell pm grant "$PACKAGE_NAME" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true

if [[ "$LAUNCH_AFTER_SEED" -eq 1 ]]; then
  adb -s "$SERIAL" shell am force-stop "$PACKAGE_NAME" >/dev/null
  adb -s "$SERIAL" shell am start -W -n "${PACKAGE_NAME}/com.petsocial.app.MainActivity" >/dev/null
fi

echo "Seeded auth session"
echo "  serial:       $SERIAL"
echo "  package:      $PACKAGE_NAME"
echo "  user_id:      $USER_ID"
echo "  profile_mode: $PROFILE_MODE"
echo "  device_state:"
adb -s "$SERIAL" shell dumpsys trust | rg 'deviceLocked=' || true
adb -s "$SERIAL" shell dumpsys window policy | rg 'isKeyguardShowing|interactiveState|screenState' || true
