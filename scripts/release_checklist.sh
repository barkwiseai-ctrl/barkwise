#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
ANDROID_DIR="$ROOT_DIR/android"

RUN_BACKEND_BARKAI_TESTS="${RUN_BACKEND_BARKAI_TESTS:-1}"
RUN_ANDROID_STAGING_UNIT_TESTS="${RUN_ANDROID_STAGING_UNIT_TESTS:-1}"
RUN_BARKAI_GOLDEN_PROMPTS="${RUN_BARKAI_GOLDEN_PROMPTS:-1}"
RUN_BARKAI_RED_TEAM_PROMPTS="${RUN_BARKAI_RED_TEAM_PROMPTS:-1}"
RUN_PHONE_SMOKE="${RUN_PHONE_SMOKE:-0}"
BARKAI_GOLDEN_BASE_URL="${BARKAI_GOLDEN_BASE_URL:-https://api.barkwiseai.com}"
BARKAI_RED_TEAM_BASE_URL="${BARKAI_RED_TEAM_BASE_URL:-$BARKAI_GOLDEN_BASE_URL}"
BACKEND_PYTHON="${BACKEND_PYTHON:-$BACKEND_DIR/.venv/bin/python}"
BACKEND_PYTEST="${BACKEND_PYTEST:-$BACKEND_DIR/.venv/bin/pytest}"
PHONE_SERIAL="${PHONE_SERIAL:-}"

step() {
  echo
  echo "==> $1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -e "$path" ]] || fail "Missing required path: $path"
}

require_executable() {
  local path="$1"
  [[ -x "$path" ]] || fail "Missing executable: $path"
}

step "Release checklist configuration"
cat <<EOF
RUN_BACKEND_BARKAI_TESTS=$RUN_BACKEND_BARKAI_TESTS
RUN_ANDROID_STAGING_UNIT_TESTS=$RUN_ANDROID_STAGING_UNIT_TESTS
RUN_BARKAI_GOLDEN_PROMPTS=$RUN_BARKAI_GOLDEN_PROMPTS
RUN_BARKAI_RED_TEAM_PROMPTS=$RUN_BARKAI_RED_TEAM_PROMPTS
RUN_PHONE_SMOKE=$RUN_PHONE_SMOKE
BARKAI_GOLDEN_BASE_URL=$BARKAI_GOLDEN_BASE_URL
BARKAI_RED_TEAM_BASE_URL=$BARKAI_RED_TEAM_BASE_URL
EOF

if [[ "$RUN_BACKEND_BARKAI_TESTS" == "1" ]]; then
  step "Backend BarkAI regression tests"
  require_executable "$BACKEND_PYTEST"
  (
    cd "$ROOT_DIR"
    "$BACKEND_PYTEST" backend/tests/test_simple_chat_service.py
  )
fi

if [[ "$RUN_ANDROID_STAGING_UNIT_TESTS" == "1" ]]; then
  step "Android staging unit tests"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:testStagingDebugUnitTest
  )
fi

if [[ "$RUN_BARKAI_GOLDEN_PROMPTS" == "1" ]]; then
  step "BarkAI golden prompts against $BARKAI_GOLDEN_BASE_URL"
  require_executable "$BACKEND_PYTHON"
  require_file "$BACKEND_DIR/scripts/run_barkai_golden_prompts.py"
  (
    cd "$ROOT_DIR"
    "$BACKEND_PYTHON" backend/scripts/run_barkai_golden_prompts.py --base-url "$BARKAI_GOLDEN_BASE_URL"
  )
fi

if [[ "$RUN_BARKAI_RED_TEAM_PROMPTS" == "1" ]]; then
  step "BarkAI red-team prompts against $BARKAI_RED_TEAM_BASE_URL"
  require_executable "$BACKEND_PYTHON"
  require_file "$BACKEND_DIR/scripts/run_barkai_red_team_prompts.py"
  (
    cd "$ROOT_DIR"
    "$BACKEND_PYTHON" backend/scripts/run_barkai_red_team_prompts.py --base-url "$BARKAI_RED_TEAM_BASE_URL"
  )
fi

if [[ "$RUN_PHONE_SMOKE" == "1" ]]; then
  step "Physical phone no-OTP onboarding smoke"
  require_executable "$ANDROID_DIR/scripts/install_no_otp_onboarding_test_phone.sh"
  if [[ -n "$PHONE_SERIAL" ]]; then
    SERIAL="$PHONE_SERIAL" "$ANDROID_DIR/scripts/install_no_otp_onboarding_test_phone.sh"
  else
    "$ANDROID_DIR/scripts/install_no_otp_onboarding_test_phone.sh"
  fi
fi

echo
echo "Release checklist passed."
