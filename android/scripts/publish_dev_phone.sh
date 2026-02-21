#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
APK_SOURCE="$ANDROID_DIR/app/build/outputs/apk/dev/debug/app-dev-debug.apk"
PACKAGE_NAME="${PACKAGE_NAME:-com.petsocial.app.dev}"
APK_NAME="${APK_NAME:-barkwise-dev-mock.apk}"
PORT="${PORT:-8787}"
SKIP_BUILD="${SKIP_BUILD:-0}"
BIND_HOST="${BIND_HOST:-0.0.0.0}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
}

detect_mdns_host() {
  local local_host=""
  if command -v scutil >/dev/null 2>&1; then
    local_host="$(scutil --get LocalHostName 2>/dev/null || true)"
  fi
  if [[ -z "$local_host" ]] && command -v hostname >/dev/null 2>&1; then
    local_host="$(hostname -s 2>/dev/null || true)"
  fi
  if [[ -z "$local_host" ]] || [[ "$local_host" == "localhost" ]] || [[ "$local_host" == "Unknown" ]] || [[ "$local_host" == "(none)" ]]; then
    return 1
  fi
  printf "%s.local" "$local_host"
}

detect_lan_ip() {
  local ip=""
  local default_iface=""
  if command -v route >/dev/null 2>&1; then
    default_iface="$(route -n get default 2>/dev/null | awk '/interface:/{print $2}' || true)"
  fi
  if command -v ipconfig >/dev/null 2>&1; then
    if [[ -n "$default_iface" ]]; then
      ip="$(ipconfig getifaddr "$default_iface" 2>/dev/null || true)"
    fi
    if [[ -z "$ip" ]]; then
      ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
    fi
    if [[ -z "$ip" ]]; then
      ip="$(ipconfig getifaddr en1 2>/dev/null || true)"
    fi
  fi
  if [[ -z "$ip" ]]; then
    ip="127.0.0.1"
  fi
  printf "%s" "$ip"
}

require_cmd adb
require_cmd python3

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "Building dev debug APK..."
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:assembleDevDebug
  )
fi

if [[ ! -f "$APK_SOURCE" ]]; then
  echo "Missing APK at $APK_SOURCE"
  echo "Build first with ./gradlew :app:assembleDevDebug"
  exit 1
fi

echo "Installing APK to physical device..."
adb -d install -r "$APK_SOURCE"
adb -d shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null

SHARE_BASE_URL="${BASE_URL:-}"
if [[ -z "$SHARE_BASE_URL" ]]; then
  AUTO_LAN_IP="$(detect_lan_ip)"
  if [[ -n "$AUTO_LAN_IP" ]] && [[ "$AUTO_LAN_IP" != "127.0.0.1" ]]; then
    SHARE_BASE_URL="http://${AUTO_LAN_IP}:${PORT}"
  elif AUTO_HOST="$(detect_mdns_host)"; then
    SHARE_BASE_URL="http://${AUTO_HOST}:${PORT}"
  fi
fi

echo
echo "Starting stable QR share server..."
if [[ -n "$SHARE_BASE_URL" ]]; then
  echo "Stable URL: $SHARE_BASE_URL/index.html"
  SKIP_BUILD=1 START_SERVER=1 APK_NAME="$APK_NAME" PORT="$PORT" BIND_HOST="$BIND_HOST" BASE_URL="$SHARE_BASE_URL" \
    "$ROOT_DIR/android/scripts/share_mock_qr.sh"
else
  echo "Could not detect LAN IP/.local hostname; using LAN auto-detect."
  SKIP_BUILD=1 START_SERVER=1 APK_NAME="$APK_NAME" PORT="$PORT" BIND_HOST="$BIND_HOST" \
    "$ROOT_DIR/android/scripts/share_mock_qr.sh"
fi
