#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
BACKEND_DIR="$ROOT_DIR/backend"
APK_DIR="$ROOT_DIR/backend/app/web/install/apk"

RUN_ANDROID_COMPILE="${RUN_ANDROID_COMPILE:-1}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-1}"
RUN_METADATA_CHECK="${RUN_METADATA_CHECK:-1}"
RUN_SMOKE_HTTP="${RUN_SMOKE_HTTP:-0}"
BASE_URL="${BASE_URL:-http://localhost:8000}"

step() {
  echo
  echo "==> $1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fail "Missing required command: $cmd"
}

check_runtime_db_not_tracked() {
  local tracked
  tracked="$(git -C "$ROOT_DIR" ls-files backend/data | rg '\.sqlite3(-wal|-shm)?$' || true)"
  if [[ -n "$tracked" ]]; then
    echo "$tracked"
    fail "Runtime sqlite files are tracked. Untrack them before release."
  fi
}

check_installer_metadata() {
  python3 - "$APK_DIR" <<'PY'
import json
import pathlib
import sys

apk_dir = pathlib.Path(sys.argv[1])
latest_path = apk_dir / "latest.json"
releases_path = apk_dir / "releases.json"

if not latest_path.exists() or not releases_path.exists():
    print("Installer metadata missing; skipping strict validation.")
    sys.exit(0)

latest = json.loads(latest_path.read_text(encoding="utf-8"))
releases = json.loads(releases_path.read_text(encoding="utf-8"))

if not isinstance(releases, list):
    raise SystemExit("releases.json must be a JSON array")
if not releases:
    raise SystemExit("releases.json must contain at least one release")

latest_version = str(latest.get("version", "")).strip()
if not latest_version:
    raise SystemExit("latest.json version is empty")

if latest_version not in {str(item.get("version", "")).strip() for item in releases if isinstance(item, dict)}:
    raise SystemExit(f"latest version '{latest_version}' not found in releases.json")

print(f"Installer metadata OK (latest={latest_version}).")
PY
}

require_cmd git
require_cmd rg
require_cmd python3

step "Git hygiene checks"
check_runtime_db_not_tracked

if [[ "$RUN_BACKEND_TESTS" == "1" ]]; then
  step "Backend tests"
  (
    cd "$BACKEND_DIR"
    pytest -q
  )
fi

if [[ "$RUN_ANDROID_COMPILE" == "1" ]]; then
  step "Android Kotlin compile checks"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:compileDevDebugKotlin :app:compileStagingDebugKotlin :app:compileProdDebugKotlin
  )
fi

if [[ "$RUN_METADATA_CHECK" == "1" ]]; then
  step "Installer metadata checks"
  check_installer_metadata
fi

if [[ "$RUN_SMOKE_HTTP" == "1" ]]; then
  step "HTTP smoke tests against $BASE_URL"
  "$BACKEND_DIR/smoke_test.sh" "$BASE_URL"
fi

echo
echo "Release preflight passed."
