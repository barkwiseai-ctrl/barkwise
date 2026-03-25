#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
BACKEND_DIR="$ROOT_DIR/backend"
APK_DIR="$ROOT_DIR/backend/app/web/install/apk"

RUN_ANDROID_COMPILE="${RUN_ANDROID_COMPILE:-1}"
RUN_ANDROID_UNIT_TESTS="${RUN_ANDROID_UNIT_TESTS:-1}"
RUN_ANDROID_LINT="${RUN_ANDROID_LINT:-1}"
RUN_ANDROID_BUNDLE="${RUN_ANDROID_BUNDLE:-1}"
RUN_ANDROID_BUNDLE_VALIDATE="${RUN_ANDROID_BUNDLE_VALIDATE:-1}"
RUN_ANDROID_MANAGED_COMPOSE_SMOKE="${RUN_ANDROID_MANAGED_COMPOSE_SMOKE:-0}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-1}"
RUN_METADATA_CHECK="${RUN_METADATA_CHECK:-1}"
RUN_SMOKE_HTTP="${RUN_SMOKE_HTTP:-0}"
BASE_URL="${BASE_URL:-http://localhost:8000}"
BUNDLE_PATH="${BUNDLE_PATH:-$ANDROID_DIR/app/build/outputs/bundle/prodRelease/app-prod-release.aab}"

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

check_maps_api_key_configured() {
  local local_props="$ANDROID_DIR/local.properties"
  local local_value=""
  if [[ -f "$local_props" ]]; then
    local_value="$(awk -F= '$1 == "MAPS_API_KEY" { sub(/^[[:space:]]+/, "", $2); print $2; exit }' "$local_props" | tr -d '\r')"
  fi
  local env_value="${MAPS_API_KEY:-}"
  if [[ -z "$env_value" && -z "$local_value" ]]; then
    fail "Missing MAPS_API_KEY. Set it in android/local.properties or environment before release."
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

validate_signed_bundle() {
  local bundle="$1"
  [[ -f "$bundle" ]] || fail "Expected AAB not found: $bundle"
  [[ -s "$bundle" ]] || fail "AAB is empty: $bundle"
  if command -v jarsigner >/dev/null 2>&1; then
    jarsigner -verify "$bundle" >/dev/null 2>&1 || fail "AAB signature verification failed"
    echo "AAB signature OK: $bundle"
  else
    echo "WARN: jarsigner not found; skipping AAB signature verification."
  fi
  if command -v bundletool >/dev/null 2>&1; then
    bundletool validate --bundle="$bundle" || fail "bundletool validation failed"
  else
    echo "WARN: bundletool not found; skipping bundle structure validation."
  fi
}

require_cmd git
require_cmd rg
require_cmd python3

step "Git hygiene checks"
check_runtime_db_not_tracked

step "Maps key checks"
check_maps_api_key_configured

if [[ "$RUN_BACKEND_TESTS" == "1" ]]; then
  step "Backend tests"
  if [[ -x "$BACKEND_DIR/.venv/bin/pytest" ]]; then
    PYTEST_CMD="$BACKEND_DIR/.venv/bin/pytest"
  else
    require_cmd pytest
    PYTEST_CMD="pytest"
  fi
  (
    cd "$BACKEND_DIR"
    "$PYTEST_CMD" -q
  )
fi

if [[ "$RUN_ANDROID_COMPILE" == "1" ]]; then
  step "Android Kotlin compile checks"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:compileStagingDebugKotlin :app:compileProviderStagingDebugKotlin :app:compileProdDebugKotlin
  )
fi

if [[ "$RUN_ANDROID_UNIT_TESTS" == "1" ]]; then
  step "Android unit tests"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:testStagingDebugUnitTest :app:testProviderStagingDebugUnitTest
  )
fi

if [[ "$RUN_ANDROID_MANAGED_COMPOSE_SMOKE" == "1" ]]; then
  step "Android managed Compose smoke tests"
  "$ANDROID_DIR/scripts/run_managed_compose_smoke.sh"
fi

if [[ "$RUN_ANDROID_LINT" == "1" ]]; then
  step "Android prod lint checks"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:lintProdRelease
  )
fi

if [[ "$RUN_ANDROID_BUNDLE" == "1" ]]; then
  step "Android signed prod bundle build"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:bundleProdRelease
  )
fi

if [[ "$RUN_ANDROID_BUNDLE_VALIDATE" == "1" ]]; then
  step "Signed AAB validation"
  validate_signed_bundle "$BUNDLE_PATH"
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
