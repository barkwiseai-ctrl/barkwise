#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android/scripts/staging_routing_common.sh
source "$SCRIPT_DIR/staging_routing_common.sh"

ensure_state_dir

existing_pid="$(read_pid_file)"
if is_pid_running "$existing_pid"; then
  echo "Routing watchdog already running (pid=$existing_pid)."
  echo "Log: $LOG_FILE"
  exit 0
fi
rm -f "$PID_FILE"

nohup "$WATCHDOG_SCRIPT" >/dev/null 2>&1 &
new_pid="$!"
sleep 1

if kill -0 "$new_pid" 2>/dev/null; then
  echo "Started routing watchdog (pid=$new_pid)."
  echo "Log: $LOG_FILE"
  exit 0
fi

echo "Failed to start routing watchdog."
exit 1
