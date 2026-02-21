#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
SHARE_DIR="$ANDROID_DIR/share/mock"
PORT="${PORT:-8787}"
SKIP_BUILD="${SKIP_BUILD:-0}"
APK_NAME="${APK_NAME:-barkwise-dev-mock.apk}"
TUNNEL="${TUNNEL:-auto}" # auto|localhostrun|cloudflared|ngrok
RETRY_SECONDS="${RETRY_SECONDS:-2}"
MAX_RETRIES="${MAX_RETRIES:-60}"
BIND_HOSTS="${BIND_HOSTS:-0.0.0.0 127.0.0.1}"

cleanup() {
  if [[ -n "${HTTP_PID:-}" ]] && kill -0 "$HTTP_PID" >/dev/null 2>&1; then
    kill "$HTTP_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TUNNEL_PID:-}" ]] && kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
    kill "$TUNNEL_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

pick_tunnel() {
  if [[ "$TUNNEL" != "auto" ]]; then
    printf "%s" "$TUNNEL"
    return
  fi
  if command -v cloudflared >/dev/null 2>&1; then
    printf "cloudflared"
    return
  fi
  if command -v ngrok >/dev/null 2>&1; then
    printf "ngrok"
    return
  fi
  printf "localhostrun"
}

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

wait_for_url() {
  local pattern="$1"
  local candidate=""
  for _ in $(seq 1 "$MAX_RETRIES"); do
    if ! kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
      echo "Tunnel process exited early. Logs:"
      tail -n 80 "$TUNNEL_LOG" || true
      return 1
    fi
    candidate="$(grep -Eo "$pattern" "$TUNNEL_LOG" | tail -n 1 || true)"
    if [[ -n "$candidate" ]]; then
      printf "%s" "${candidate%/}"
      return 0
    fi
    sleep "$RETRY_SECONDS"
  done
  return 1
}

wait_for_http_ok() {
  local url="$1"
  local status_line=""
  for _ in $(seq 1 "$MAX_RETRIES"); do
    status_line="$(curl -sSI --max-time 20 "$url" | head -n 1 || true)"
    if [[ "$status_line" == *" 200 "* ]] || [[ "$status_line" == *" 30"* ]]; then
      return 0
    fi
    sleep "$RETRY_SECONDS"
  done
  return 1
}

wait_for_localhostrun_url() {
  local candidate=""
  local filtered=""
  for _ in $(seq 1 "$MAX_RETRIES"); do
    if ! kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
      echo "Tunnel process exited early. Logs:"
      tail -n 80 "$TUNNEL_LOG" || true
      return 1
    fi
    candidate="$(
      grep -E "tunneled with tls termination" "$TUNNEL_LOG" \
        | grep -Eo "https?://[A-Za-z0-9.-]+" \
        | grep -Ev "admin\.localhost\.run" \
        | grep -E "lhr\.life|localhost\.run" \
        | tail -n 1 || true
    )"
    if [[ -n "$candidate" ]]; then
      filtered="${candidate%/}"
      printf "%s" "$filtered"
      return 0
    fi
    sleep "$RETRY_SECONDS"
  done
  return 1
}

start_http_server() {
  local host=""
  for host in $BIND_HOSTS; do
    echo "Trying local file server on ${host}:${PORT}..."
    (
      cd "$SHARE_DIR"
      python3 -m http.server "$PORT" --bind "$host" >/tmp/barkwise-mock-http.log 2>&1
    ) &
    HTTP_PID=$!
    sleep 1

    if ! kill -0 "$HTTP_PID" >/dev/null 2>&1; then
      continue
    fi

    if wait_for_http_ok "http://127.0.0.1:${PORT}/index.html"; then
      echo "Local file server ready on ${host}:${PORT}"
      return 0
    fi

    kill "$HTTP_PID" >/dev/null 2>&1 || true
  done
  return 1
}

echo "Preparing mock APK and local landing page..."
require_cmd python3
require_cmd curl
(
  cd "$ROOT_DIR"
  SKIP_BUILD="$SKIP_BUILD" START_SERVER=0 APK_NAME="$APK_NAME" ./android/scripts/share_mock_qr.sh >/dev/null
)

if ! start_http_server; then
  echo "Local share server did not become ready on http://127.0.0.1:${PORT}/index.html"
  echo "Server log:"
  tail -n 60 /tmp/barkwise-mock-http.log || true
  exit 1
fi

TUNNEL_KIND="$(pick_tunnel)"
echo "Opening public tunnel via: ${TUNNEL_KIND}"

TUNNEL_LOG="/tmp/barkwise-mock-tunnel.log"
: > "$TUNNEL_LOG"

case "$TUNNEL_KIND" in
  cloudflared)
    cloudflared tunnel --url "http://127.0.0.1:${PORT}" --no-autoupdate >"$TUNNEL_LOG" 2>&1 &
    TUNNEL_PID=$!
    URL_PATTERN='https?://[A-Za-z0-9.-]*trycloudflare\.com'
    ;;
  ngrok)
    ngrok http "$PORT" >"$TUNNEL_LOG" 2>&1 &
    TUNNEL_PID=$!
    URL_PATTERN='https?://[A-Za-z0-9.-]*(ngrok-free\.app|ngrok\.io)'
    ;;
  localhostrun)
    if ! command -v ssh >/dev/null 2>&1; then
      echo "Missing ssh command; cannot use localhost.run tunnel."
      echo "Install cloudflared or ngrok, or set TUNNEL explicitly."
      exit 1
    fi
    ssh -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o StrictHostKeyChecking=accept-new \
      -R 80:localhost:"$PORT" nokey@localhost.run >"$TUNNEL_LOG" 2>&1 &
    TUNNEL_PID=$!
    URL_PATTERN='https?://[A-Za-z0-9.-]*(lhr\.life|localhost\.run)'
    ;;
  *)
    echo "Unsupported TUNNEL value: ${TUNNEL_KIND}"
    exit 1
    ;;
esac

PUBLIC_BASE_URL=""
if [[ "$TUNNEL_KIND" == "localhostrun" ]]; then
  PUBLIC_BASE_URL="$(wait_for_localhostrun_url || true)"
else
  PUBLIC_BASE_URL="$(wait_for_url "$URL_PATTERN" || true)"
fi

if [[ -z "$PUBLIC_BASE_URL" ]]; then
  echo "Could not detect public URL automatically yet."
  echo "Check logs: $TUNNEL_LOG"
  tail -n 80 "$TUNNEL_LOG" || true
  exit 1
fi

LANDING_URL="${PUBLIC_BASE_URL}/index.html"
APK_URL="${PUBLIC_BASE_URL}/${APK_NAME}"
ENCODED_LANDING_URL="$(urlencode "$LANDING_URL")"
QR_URL_PRIMARY="https://quickchart.io/qr?size=700&text=${ENCODED_LANDING_URL}"
QR_URL_FALLBACK_1="https://api.qrserver.com/v1/create-qr-code/?size=700x700&data=${ENCODED_LANDING_URL}"
QR_URL_FALLBACK_2="https://chart.googleapis.com/chart?cht=qr&chs=700x700&chl=${ENCODED_LANDING_URL}"
QR_URL="$QR_URL_PRIMARY"

if ! wait_for_http_ok "$LANDING_URL"; then
  echo "Public landing page did not become reachable: $LANDING_URL"
  echo "Check logs: $TUNNEL_LOG"
  exit 1
fi

if ! wait_for_http_ok "$APK_URL"; then
  echo "Public APK URL did not become reachable: $APK_URL"
  echo "Check logs: $TUNNEL_LOG"
  exit 1
fi

echo
echo "Public mock sharing is live:"
echo "Landing URL:  ${LANDING_URL}"
echo "Direct APK:   ${APK_URL}"
echo "QR image URL: ${QR_URL}"

if command -v curl >/dev/null 2>&1; then
  for candidate in "$QR_URL_PRIMARY" "$QR_URL_FALLBACK_1" "$QR_URL_FALLBACK_2"; do
    if curl -fsSL "$candidate" -o "$SHARE_DIR/qr-public.png" >/dev/null 2>&1; then
      echo "QR image URL: ${candidate}"
      echo "QR PNG:       $SHARE_DIR/qr-public.png"
      break
    fi
  done
fi

cat > "$SHARE_DIR/tester-instructions.txt" <<EOF
BarkWise mock install is live.

1) Open on phone browser:
   ${LANDING_URL}
2) Tap Download APK and install.
3) If blocked, allow unknown app installs for your browser/files app.
4) Launch "BarkWise Dev".

Direct APK:
${APK_URL}
EOF
echo "Tester guide: $SHARE_DIR/tester-instructions.txt"

echo
echo "Keep this terminal running while testers install."
echo "Press Ctrl+C to stop both the tunnel and local server."
wait "$TUNNEL_PID"
