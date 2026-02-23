#!/bin/zsh
set -u

ROOT_DIR="/Users/yingxu/public-repos/pet-social-app/backend"
BOT_SCRIPT="$ROOT_DIR/scripts/api_bots.py"
SEED_SCRIPT="$ROOT_DIR/scripts/api_bot_seed_activity.py"
RUNS_DIR="$ROOT_DIR/data/api-bot-runs"
LOG_DIR="$ROOT_DIR/data"
LOG_FILE="$LOG_DIR/api-bot-loop.log"
LOCK_DIR="$LOG_DIR/api-bot-loop.lock"

BASE_URL="${BASE_URL:-http://localhost:8000}"
USERS="${USERS:-annika,snowy,sesame,pepsi,billie,buddy}"
PASSWORD="${PASSWORD:-petsocial-demo}"
CONCURRENCY="${CONCURRENCY:-6}"
ITERATIONS="${ITERATIONS:-24}"
MIN_DELAY_MS="${MIN_DELAY_MS:-600}"
MAX_DELAY_MS="${MAX_DELAY_MS:-1800}"
INTERVAL_HOURS="${INTERVAL_HOURS:-3}"
ANNIKA_USER="${ANNIKA_USER:-annika}"
ANNIKA_FORCE_POSTS="${ANNIKA_FORCE_POSTS:-2}"
COLLENSO_GROUP_NAME="${COLLENSO_GROUP_NAME:-Collenso Dog Park}"
COLLENSO_SUBURB="${COLLENSO_SUBURB:-Sunshine West}"
COLLENSO_OWNER="${COLLENSO_OWNER:-annika}"
SEED_EACH_CYCLE="${SEED_EACH_CYCLE:-0}"

mkdir -p "$RUNS_DIR" "$LOG_DIR"

if [[ -d "$LOCK_DIR" ]]; then
  if [[ -f "$LOCK_DIR/pid" ]]; then
    existing_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
    if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" 2>/dev/null; then
      printf '[%s] %s\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "Another API bot loop is already running (pid=$existing_pid)." | tee -a "$LOG_FILE"
      exit 0
    fi
  fi
  rm -rf "$LOCK_DIR"
fi

mkdir -p "$LOCK_DIR"
echo "$$" > "$LOCK_DIR/pid"
trap 'rm -rf "$LOCK_DIR"' EXIT INT TERM

log() {
  local ts
  ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  printf '[%s] %s\n' "$ts" "$1" | tee -a "$LOG_FILE"
}

run_cycle() {
  local cycle_ts summary_file rc
  cycle_ts="$(date -u +"%Y%m%dT%H%M%SZ")"
  summary_file="$RUNS_DIR/summary-$cycle_ts.json"

  log "Starting API bot cycle. base_url=$BASE_URL users=$USERS concurrency=$CONCURRENCY iterations=$ITERATIONS"

  if [[ "$SEED_EACH_CYCLE" == "1" ]]; then
    python3 "$SEED_SCRIPT" \
      --base-url "$BASE_URL" \
      --users "$USERS" \
      --password "$PASSWORD" >>"$LOG_FILE" 2>&1
    rc=$?
    log "Seed activity completed with exit_code=$rc"
  fi

  python3 "$BOT_SCRIPT" \
    --base-url "$BASE_URL" \
    --users "$USERS" \
    --password "$PASSWORD" \
    --concurrency "$CONCURRENCY" \
    --iterations "$ITERATIONS" \
    --min-delay-ms "$MIN_DELAY_MS" \
    --max-delay-ms "$MAX_DELAY_MS" \
    --annika-user "$ANNIKA_USER" \
    --annika-force-posts "$ANNIKA_FORCE_POSTS" \
    --collenso-group-name "$COLLENSO_GROUP_NAME" \
    --collenso-suburb "$COLLENSO_SUBURB" \
    --collenso-owner "$COLLENSO_OWNER" \
    --json-out "$summary_file" >>"$LOG_FILE" 2>&1
  rc=$?
  log "Bot activity completed with exit_code=$rc summary=$summary_file"
}

sleep_seconds=$(( INTERVAL_HOURS * 3600 ))
if (( sleep_seconds <= 0 )); then
  log "Invalid INTERVAL_HOURS=$INTERVAL_HOURS; must be > 0"
  exit 1
fi

log "API bot loop started. Interval=${INTERVAL_HOURS}h log_file=$LOG_FILE"
while true; do
  run_cycle
  log "Sleeping for ${INTERVAL_HOURS}h"
  sleep "$sleep_seconds"
done
