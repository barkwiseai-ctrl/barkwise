#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SHARE_DIR="$ROOT_DIR/android/share/mock"
LOG_FILE="$SHARE_DIR/tunnel-watchdog.log"

mkdir -p "$SHARE_DIR"

RESTART_DELAY_SECONDS="${RESTART_DELAY_SECONDS:-3}"
SKIP_BUILD="${SKIP_BUILD:-1}"
MAX_RETRIES="${MAX_RETRIES:-120}"
RETRY_SECONDS="${RETRY_SECONDS:-2}"

echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Starting tunnel watchdog" | tee -a "$LOG_FILE"
echo "Log file: $LOG_FILE"
echo "State file: $SHARE_DIR/public-tunnel-state.txt"

while true; do
  echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Launching public tunnel..." | tee -a "$LOG_FILE"
  if SKIP_BUILD="$SKIP_BUILD" MAX_RETRIES="$MAX_RETRIES" RETRY_SECONDS="$RETRY_SECONDS" \
    "$ROOT_DIR/android/scripts/share_mock_public_tunnel.sh" >>"$LOG_FILE" 2>&1; then
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Tunnel exited normally; restarting in ${RESTART_DELAY_SECONDS}s" | tee -a "$LOG_FILE"
  else
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Tunnel exited with error; restarting in ${RESTART_DELAY_SECONDS}s" | tee -a "$LOG_FILE"
  fi
  sleep "$RESTART_DELAY_SECONDS"
done
