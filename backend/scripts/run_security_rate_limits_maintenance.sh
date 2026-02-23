#!/bin/zsh
set -u

ROOT_DIR="/Users/yingxu/public-repos/pet-social-app/backend"
EXPORT_SCRIPT="$ROOT_DIR/scripts/export_security_rate_limits_snapshot.py"
CLEANUP_SCRIPT="$ROOT_DIR/scripts/cleanup_security_rate_limits_snapshots.py"

BASE_URL="${BASE_URL:-http://localhost:8000}"
REQUESTER_USER_ID="${REQUESTER_USER_ID:-user_1}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/data/security-audit-snapshots}"
RETAIN_DAYS="${RETAIN_DAYS:-14}"
EXPORT_TIMEOUT_SECONDS="${EXPORT_TIMEOUT_SECONDS:-10}"
DRY_RUN_CLEANUP="${DRY_RUN_CLEANUP:-0}"
SKIP_EXPORT="${SKIP_EXPORT:-0}"
LOG_FILE="${LOG_FILE:-$OUTPUT_DIR/export.log}"

mkdir -p "$OUTPUT_DIR"

log() {
  local ts
  ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  printf '[%s] %s\n' "$ts" "$1" | tee -a "$LOG_FILE"
}

run_export() {
  local rc
  if [[ "$SKIP_EXPORT" == "1" ]]; then
    log "Skipping export (SKIP_EXPORT=1)"
    return 0
  fi
  if [[ -z "$AUTH_TOKEN" ]]; then
    log "Missing AUTH_TOKEN; cannot export snapshot"
    return 2
  fi
  log "Exporting security rate-limit snapshot"
  "$EXPORT_SCRIPT" \
    --base-url "$BASE_URL" \
    --requester-user-id "$REQUESTER_USER_ID" \
    --token "$AUTH_TOKEN" \
    --output-dir "$OUTPUT_DIR" \
    --timeout-seconds "$EXPORT_TIMEOUT_SECONDS" >>"$LOG_FILE" 2>&1
  rc=$?
  log "Export completed with exit_code=$rc"
  return $rc
}

run_cleanup() {
  local rc dry_flag
  dry_flag=()
  if [[ "$DRY_RUN_CLEANUP" == "1" ]]; then
    dry_flag=(--dry-run)
  fi
  log "Running snapshot cleanup retain_days=$RETAIN_DAYS dry_run=$DRY_RUN_CLEANUP"
  "$CLEANUP_SCRIPT" \
    --output-dir "$OUTPUT_DIR" \
    --retain-days "$RETAIN_DAYS" \
    "${dry_flag[@]}" >>"$LOG_FILE" 2>&1
  rc=$?
  log "Cleanup completed with exit_code=$rc"
  return $rc
}

log "Security metrics maintenance start"
run_export
export_rc=$?
run_cleanup
cleanup_rc=$?
log "Security metrics maintenance done export_rc=$export_rc cleanup_rc=$cleanup_rc"

if (( export_rc != 0 )); then
  exit $export_rc
fi
exit $cleanup_rc
