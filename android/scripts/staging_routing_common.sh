#!/usr/bin/env bash
# Shared constants/helpers for staging local routing scripts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${STATE_DIR:-$ROOT_DIR/android/share/staging-routing}"
PID_FILE="${PID_FILE:-$STATE_DIR/adb-reverse-watchdog.pid}"
LOG_FILE="${LOG_FILE:-$STATE_DIR/adb-reverse-watchdog.log}"
WATCHDOG_SCRIPT="${WATCHDOG_SCRIPT:-$ROOT_DIR/android/scripts/keep_staging_local_routing_alive.sh}"

HOST_PORT="${HOST_PORT:-8000}"
DEVICE_PORT="${DEVICE_PORT:-8000}"
CHECK_SECONDS="${CHECK_SECONDS:-5}"
PACKAGE_NAME="${PACKAGE_NAME:-com.petsocial.app.staging}"

ensure_state_dir() {
  mkdir -p "$STATE_DIR"
}

read_pid_file() {
  if [[ -f "$PID_FILE" ]]; then
    cat "$PID_FILE" 2>/dev/null || true
  fi
}

is_pid_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}
