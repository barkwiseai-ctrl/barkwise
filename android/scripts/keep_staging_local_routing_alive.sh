#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android/scripts/staging_routing_common.sh
source "$SCRIPT_DIR/staging_routing_common.sh"

ensure_state_dir

log() {
  local ts
  ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  printf '[%s] %s\n' "$ts" "$1" | tee -a "$LOG_FILE"
}

echo "$$" >"$PID_FILE"
trap 'rm -f "$PID_FILE"' EXIT INT TERM

log "Starting staging routing watchdog for ${PACKAGE_NAME} (device tcp:${DEVICE_PORT} -> host tcp:${HOST_PORT})"

last_state=""

while true; do
  devices_output="$(adb devices 2>/dev/null || true)"
  device_id="$(printf '%s\n' "$devices_output" | awk 'NR > 1 && $2 == "device" {print $1; exit}')"
  if [[ -z "${device_id}" ]]; then
    state="no_device"
    if [[ "$state" != "$last_state" ]]; then
      log "No active adb device detected; waiting."
    fi
    last_state="$state"
    sleep "$CHECK_SECONDS"
    continue
  fi

  reverse_list="$(adb -d reverse --list 2>/dev/null || true)"
  if printf '%s\n' "$reverse_list" | grep -Eq "tcp:${DEVICE_PORT}[[:space:]]+tcp:${HOST_PORT}$"; then
    state="mapped"
    if [[ "$state" != "$last_state" ]]; then
      log "Routing active on device ${device_id}: tcp:${DEVICE_PORT} -> tcp:${HOST_PORT}"
    fi
    last_state="$state"
    sleep "$CHECK_SECONDS"
    continue
  fi

  if adb -d reverse "tcp:${DEVICE_PORT}" "tcp:${HOST_PORT}" >/dev/null 2>&1; then
    last_state="mapped"
    log "Applied adb reverse on ${device_id}: tcp:${DEVICE_PORT} -> tcp:${HOST_PORT}"
  else
    state="reverse_failed"
    if [[ "$state" != "$last_state" ]]; then
      log "Failed to apply adb reverse; will retry."
    fi
    last_state="$state"
  fi

  sleep "$CHECK_SECONDS"
done
