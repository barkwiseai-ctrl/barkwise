#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
SHARE_DIR="$ANDROID_DIR/share/mock"
APK_SOURCE="$ANDROID_DIR/app/build/outputs/apk/dev/debug/app-dev-debug.apk"
APK_NAME="${APK_NAME:-barkwise-dev-mock.apk}"
PORT="${PORT:-8787}"
START_SERVER="${START_SERVER:-1}"
SKIP_BUILD="${SKIP_BUILD:-0}"
BIND_HOST="${BIND_HOST:-0.0.0.0}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
}

urlencode() {
  local value="$1"
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$value"
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
  if [[ -z "$ip" ]] && command -v hostname >/dev/null 2>&1; then
    ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  if [[ -z "$ip" ]]; then
    ip="127.0.0.1"
  fi
  printf "%s" "$ip"
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

USER_BASE_URL="${BASE_URL:-}"
LAN_IP="${LAN_IP:-$(detect_lan_ip)}"
BASE_HOST="${BASE_HOST:-$(detect_mdns_host || true)}"
if [[ -n "$USER_BASE_URL" ]]; then
  BASE_URL="$USER_BASE_URL"
else
  BASE_URL="http://${LAN_IP}:${PORT}"
  if [[ "$LAN_IP" == "127.0.0.1" ]] && [[ -n "$BASE_HOST" ]]; then
    BASE_URL="http://${BASE_HOST}:${PORT}"
  fi
fi
DOWNLOAD_URL="${BASE_URL}/${APK_NAME}"
LANDING_URL="${BASE_URL}/index.html"

require_cmd python3

ENCODED_LANDING_URL="$(urlencode "$LANDING_URL")"
QR_URL_PRIMARY="https://quickchart.io/qr?size=700&text=${ENCODED_LANDING_URL}"
QR_URL_FALLBACK_1="https://api.qrserver.com/v1/create-qr-code/?size=700x700&data=${ENCODED_LANDING_URL}"
QR_URL_FALLBACK_2="https://chart.googleapis.com/chart?cht=qr&chs=700x700&chl=${ENCODED_LANDING_URL}"
QR_URL="$QR_URL_PRIMARY"

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "Building dev debug APK (mock data enabled)..."
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:assembleDevDebug
  )
fi

if [[ ! -f "$APK_SOURCE" ]]; then
  echo "Missing APK at $APK_SOURCE"
  echo "Run with SKIP_BUILD=0 or build manually with ./gradlew :app:assembleDevDebug"
  exit 1
fi

mkdir -p "$SHARE_DIR"
cp "$APK_SOURCE" "$SHARE_DIR/$APK_NAME"

APK_SHA256=""
if command -v shasum >/dev/null 2>&1; then
  APK_SHA256="$(shasum -a 256 "$SHARE_DIR/$APK_NAME" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
  APK_SHA256="$(sha256sum "$SHARE_DIR/$APK_NAME" | awk '{print $1}')"
fi

cat > "$SHARE_DIR/index.html" <<EOF
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>BarkWise Mock Preview</title>
    <style>
      :root {
        --bg: #f2efe9;
        --card: #fffdf9;
        --ink: #1f2937;
        --accent: #0f766e;
      }
      body {
        margin: 0;
        background: radial-gradient(circle at top right, #fff4d9, var(--bg));
        font-family: "Avenir Next", "Segoe UI", sans-serif;
        color: var(--ink);
      }
      .wrap {
        max-width: 680px;
        margin: 0 auto;
        padding: 28px 16px 48px;
      }
      .card {
        background: var(--card);
        border-radius: 16px;
        padding: 20px;
        box-shadow: 0 10px 24px rgba(0, 0, 0, 0.08);
      }
      h1 {
        margin: 0 0 8px;
        font-size: 1.5rem;
      }
      p, li {
        line-height: 1.45;
      }
      .btn {
        display: inline-block;
        margin-top: 8px;
        background: var(--accent);
        color: white;
        text-decoration: none;
        padding: 12px 16px;
        border-radius: 10px;
        font-weight: 600;
      }
      code {
        background: #ececec;
        padding: 2px 6px;
        border-radius: 6px;
      }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="card">
        <h1>BarkWise Mock Preview (Android)</h1>
        <p>This build is fully mock-backed with seeded data and interactive flows.</p>
        <a class="btn" href="./${APK_NAME}">Download APK</a>
        <ol>
          <li>Download and open the APK.</li>
          <li>Allow install from browser/files when prompted.</li>
          <li>Launch <code>BarkWise Dev</code> and explore Services, Community, BarkAI, Messages, and Profile.</li>
        </ol>
        <p><strong>APK SHA-256:</strong> <code>${APK_SHA256:-unavailable}</code></p>
        <p>If install is blocked, Android path is usually Settings -> Security -> Install unknown apps.</p>
      </div>
    </div>
  </body>
</html>
EOF

echo
echo "Mock share package ready:"
echo "APK:          $SHARE_DIR/$APK_NAME"
echo "Landing URL:  $LANDING_URL"
echo "Direct APK:   $DOWNLOAD_URL"
echo "QR image URL: $QR_URL"
if [[ "$LAN_IP" == "127.0.0.1" && -z "$USER_BASE_URL" ]]; then
  echo "Warning: host detection fell back to localhost."
  echo "Set BASE_HOST or LAN_IP manually, for example:"
  echo "  BASE_HOST=My-MacBook.local ./android/scripts/share_mock_qr.sh"
  echo "  LAN_IP=192.168.1.23 ./android/scripts/share_mock_qr.sh"
fi

if command -v curl >/dev/null 2>&1; then
  for candidate in "$QR_URL_PRIMARY" "$QR_URL_FALLBACK_1" "$QR_URL_FALLBACK_2"; do
    if curl -fsSL "$candidate" -o "$SHARE_DIR/qr.png" >/dev/null 2>&1; then
      echo "QR image URL: $candidate"
      echo "QR PNG:       $SHARE_DIR/qr.png"
      break
    fi
  done
fi

if [[ "$START_SERVER" == "1" ]]; then
  echo
  echo "Starting local share server on ${BIND_HOST}:${PORT}..."
  echo "Keep this terminal running while people install."
  (
    cd "$SHARE_DIR"
    python3 -m http.server "$PORT" --bind "$BIND_HOST"
  )
fi
