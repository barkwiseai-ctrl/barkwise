#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=android/scripts/staging_routing_common.sh
source "$SCRIPT_DIR/staging_routing_common.sh"

stopped=0

pid="$(read_pid_file)"
if is_pid_running "$pid"; then
  kill "$pid" >/dev/null 2>&1 || true
  stopped=1
  echo "Stopped routing watchdog (pid=$pid)."
fi
rm -f "$PID_FILE"

pkill -f keep_staging_local_routing_alive.sh >/dev/null 2>&1 || true

if [[ "$stopped" -eq 0 ]]; then
  echo "No routing watchdog process was running."
fi
