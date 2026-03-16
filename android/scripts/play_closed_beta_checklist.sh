#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
APP_DIR="$ANDROID_DIR/app"
OUT_DIR="$ANDROID_DIR/share/release"
UTC_TS="$(date -u +"%Y%m%dT%H%M%SZ")"
REPORT_PATH="$OUT_DIR/closed_beta_readiness_${UTC_TS}.md"
LATEST_REPORT_PATH="$OUT_DIR/closed_beta_readiness_latest.md"
AAB_PATH="${AAB_PATH:-$ANDROID_DIR/app/build/outputs/bundle/prodRelease/app-prod-release.aab}"
RUN_PREFLIGHT_UNSIGNED="${RUN_PREFLIGHT_UNSIGNED:-1}"
RUN_PREFLIGHT_SIGNED="${RUN_PREFLIGHT_SIGNED:-1}"
RUN_BACKEND_POLICY_TESTS="${RUN_BACKEND_POLICY_TESTS:-1}"
BACKEND_PYTHON="${BACKEND_PYTHON:-$ROOT_DIR/backend/.venv/bin/python}"
CHECKLIST_GRADLE_USER_HOME="${CHECKLIST_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-$ANDROID_DIR/.gradle}}"
TARGET_PACKAGE_ID="${TARGET_PACKAGE_ID:-com.barkwise.app}"

mkdir -p "$OUT_DIR" "$CHECKLIST_GRADLE_USER_HOME"

PASS_COUNT=0
BLOCKED_COUNT=0
TODO_COUNT=0

preflight_unsigned_log="$OUT_DIR/preflight_unsigned_${UTC_TS}.log"
preflight_signed_log="$OUT_DIR/preflight_signed_${UTC_TS}.log"
signed_preflight_passed=0
policy_tests_log="$OUT_DIR/backend_policy_tests_${UTC_TS}.log"
tester_prepare_log="$OUT_DIR/tester_prepare_${UTC_TS}.log"
tester_csv="$OUT_DIR/closed_beta_testers.csv"
tester_import_csv="$OUT_DIR/closed_beta_testers_play_import.csv"
qr_signoff_csv="$OUT_DIR/physical_qr_signoff.csv"

read_local_property() {
  local key="$1"
  local local_props="$ANDROID_DIR/local.properties"
  if [[ ! -f "$local_props" ]]; then
    return 1
  fi
  awk -F= -v target="$key" '$1 == target { sub(/^[[:space:]]+/, "", $2); print $2; exit }' "$local_props"
}

resolve_config_value() {
  local key="$1"
  local default_value="$2"
  local env_value="${!key:-}"
  if [[ -n "$env_value" ]]; then
    echo "$env_value"
    return 0
  fi
  local local_value
  local_value="$(read_local_property "$key" || true)"
  if [[ -n "$local_value" ]]; then
    echo "$local_value"
    return 0
  fi
  echo "$default_value"
}

check_key_present() {
  local key="$1"
  local env_value="${!key:-}"
  if [[ -n "$env_value" ]]; then
    return 0
  fi
  local local_value
  local_value="$(read_local_property "$key" || true)"
  [[ -n "$local_value" ]]
}

status_line() {
  local status="$1"
  local item="$2"
  local detail="$3"
  case "$status" in
    PASS) PASS_COUNT=$((PASS_COUNT + 1)) ;;
    BLOCKED) BLOCKED_COUNT=$((BLOCKED_COUNT + 1)) ;;
    TODO) TODO_COUNT=$((TODO_COUNT + 1)) ;;
  esac
  printf '| %s | %s | %s |\n' "$status" "$item" "$detail" >> "$REPORT_PATH"
}

app_id="$(rg --no-filename --only-matching --replace '$1' 'applicationId\s*=\s*"([^"]+)"' "$APP_DIR/build.gradle.kts" | head -n1 || true)"
version_code="$(resolve_config_value "BARKWISE_VERSION_CODE" "1")"
version_name="$(resolve_config_value "BARKWISE_VERSION_NAME" "0.1.0")"

{
  echo "# Play Closed Beta Readiness"
  echo
  echo "- Generated (UTC): ${UTC_TS}"
  echo "- Target package: \`${TARGET_PACKAGE_ID}\`"
  echo "- Resolved package: \`${app_id:-unknown}\`"
  echo "- Resolved versionCode: \`${version_code}\`"
  echo "- Resolved versionName: \`${version_name}\`"
  echo "- AAB path: \`${AAB_PATH}\`"
  echo
  echo "| Status | Check | Detail |"
  echo "|---|---|---|"
} > "$REPORT_PATH"

if [[ "$app_id" == "$TARGET_PACKAGE_ID" ]]; then
  status_line "PASS" "Package Id" "Application id matches \`${TARGET_PACKAGE_ID}\`."
else
  status_line "BLOCKED" "Package Id" "Application id mismatch. Expected \`${TARGET_PACKAGE_ID}\`, got \`${app_id:-unknown}\`."
fi

if rg -q 'manifestPlaceholders\["usesCleartextTraffic"\]\s*=\s*"false"' "$APP_DIR/build.gradle.kts"; then
  status_line "PASS" "Prod cleartext disabled" "Prod flavor enforces \`usesCleartextTraffic=false\`."
else
  status_line "BLOCKED" "Prod cleartext disabled" "Prod cleartext setting not enforced in Gradle config."
fi

if check_key_present "BARKWISE_RELEASE_STORE_FILE" \
  && check_key_present "BARKWISE_RELEASE_STORE_PASSWORD" \
  && check_key_present "BARKWISE_RELEASE_KEY_ALIAS" \
  && check_key_present "BARKWISE_RELEASE_KEY_PASSWORD"; then
  status_line "PASS" "Release signing config" "All required signing keys are configured via env or local properties."
else
  status_line "BLOCKED" "Release signing config" "Missing one or more required signing keys (store file/password, key alias/password)."
fi

if [[ "$RUN_PREFLIGHT_UNSIGNED" == "1" ]]; then
  if GRADLE_USER_HOME="$CHECKLIST_GRADLE_USER_HOME" RUN_ANDROID_BUNDLE=0 RUN_ANDROID_BUNDLE_VALIDATE=0 "$ANDROID_DIR/scripts/release_preflight.sh" >"$preflight_unsigned_log" 2>&1; then
    status_line "PASS" "Unsigned release preflight" "Passed. Log: \`$preflight_unsigned_log\`."
  else
    status_line "BLOCKED" "Unsigned release preflight" "Failed. Log: \`$preflight_unsigned_log\`."
  fi
else
  status_line "TODO" "Unsigned release preflight" "Skipped (RUN_PREFLIGHT_UNSIGNED=0)."
fi

if [[ "$RUN_PREFLIGHT_SIGNED" == "1" ]]; then
  if GRADLE_USER_HOME="$CHECKLIST_GRADLE_USER_HOME" "$ANDROID_DIR/scripts/release_preflight.sh" >"$preflight_signed_log" 2>&1; then
    signed_preflight_passed=1
    status_line "PASS" "Signed release preflight" "Passed. Log: \`$preflight_signed_log\`."
  else
    signed_preflight_passed=0
    status_line "BLOCKED" "Signed release preflight" "Failed. Log: \`$preflight_signed_log\`."
  fi
else
  signed_preflight_passed=0
  status_line "TODO" "Signed release preflight" "Skipped (RUN_PREFLIGHT_SIGNED=0)."
fi

if [[ "$signed_preflight_passed" -eq 1 ]]; then
  if [[ -f "$AAB_PATH" && -s "$AAB_PATH" ]]; then
    status_line "PASS" "Signed AAB exists" "Artifact found and non-empty."
    if command -v jarsigner >/dev/null 2>&1; then
      if jarsigner -verify "$AAB_PATH" >/dev/null 2>&1; then
        status_line "PASS" "AAB signature verification" "Verified with \`jarsigner\`."
      else
        status_line "BLOCKED" "AAB signature verification" "Signature validation failed with \`jarsigner\`."
      fi
    else
      status_line "TODO" "AAB signature verification" "\`jarsigner\` not installed locally."
    fi
  else
    status_line "BLOCKED" "Signed AAB exists" "Signed preflight passed but artifact missing at \`$AAB_PATH\`."
    status_line "BLOCKED" "AAB signature verification" "Skipped because signed AAB is missing."
  fi
else
  if [[ -f "$AAB_PATH" && -s "$AAB_PATH" ]]; then
    status_line "TODO" "Signed AAB exists" "AAB is present on disk but signed preflight failed this run; treat as stale until signing config is fixed."
    status_line "TODO" "AAB signature verification" "Skipped because signed preflight did not pass in this run."
  else
    status_line "BLOCKED" "Signed AAB exists" "Artifact missing or empty at \`$AAB_PATH\`."
    status_line "BLOCKED" "AAB signature verification" "Skipped because signed AAB is missing."
  fi
fi

if [[ -f "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrCodeUtils.kt" ]] \
  && rg -q 'fun parseQrPayload' "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrCodeUtils.kt" \
  && rg -q 'invite_token' "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrCodeUtils.kt"; then
  status_line "PASS" "QR parser implementation" "Invite token parsing is present in app QR utility."
else
  status_line "BLOCKED" "QR parser implementation" "QR parser/invite token handling missing."
fi

if [[ -f "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrScannerSheet.kt" ]] \
  && rg -q 'Manifest.permission.CAMERA' "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrScannerSheet.kt" \
  && rg -q 'BarcodeScanning.getClient' "$APP_DIR/src/main/java/com/petsocial/app/ui/qr/QrScannerSheet.kt"; then
  status_line "PASS" "In-app QR scanner" "Camera permission and ML Kit scanner integration detected."
else
  status_line "BLOCKED" "In-app QR scanner" "Scanner implementation or camera permission wiring missing."
fi

if rg -q 'Scan Invite QR' "$APP_DIR/src/main/java/com/petsocial/app/ui/screens/CommunityScreen.kt"; then
  status_line "PASS" "Community scan entrypoint" "\"Scan Invite QR\" entry exists in Community UI."
else
  status_line "BLOCKED" "Community scan entrypoint" "Community QR scan entrypoint missing."
fi

if [[ -f "$APP_DIR/src/test/java/com/petsocial/app/ui/qr/QrCodeUtilsTest.kt" ]]; then
  status_line "PASS" "QR parser unit tests" "Unit tests for QR parsing are present."
else
  status_line "BLOCKED" "QR parser unit tests" "No QR parser unit tests found."
fi

if rg -F -q '@router.post("/invite"' "$ROOT_DIR/backend/app/routers/auth.py" \
  && rg -F -q '@router.post("/otp/request"' "$ROOT_DIR/backend/app/routers/auth.py" \
  && rg -F -q '@router.post("/otp/verify"' "$ROOT_DIR/backend/app/routers/auth.py" \
  && rg -F -q '@router.post("/logout"' "$ROOT_DIR/backend/app/routers/auth.py" \
  && rg -F -q '@router.delete("/me"' "$ROOT_DIR/backend/app/routers/auth.py"; then
  status_line "PASS" "Auth API endpoints" "Invite, OTP, logout, and delete-me endpoints are present."
else
  status_line "BLOCKED" "Auth API endpoints" "One or more auth endpoints are missing."
fi

if rg -F -q '@router.get("/threads"' "$ROOT_DIR/backend/app/routers/messages.py" \
  && rg -F -q '@router.get("/threads/{thread_id}"' "$ROOT_DIR/backend/app/routers/messages.py" \
  && rg -F -q '@router.post("/threads/{thread_id}/messages"' "$ROOT_DIR/backend/app/routers/messages.py" \
  && rg -F -q '@router.post("/threads/{thread_id}/read"' "$ROOT_DIR/backend/app/routers/messages.py"; then
  status_line "PASS" "Messaging API endpoints" "Thread list, thread detail, send, and read endpoints are present."
else
  status_line "BLOCKED" "Messaging API endpoints" "One or more messaging endpoints are missing."
fi

if rg -q 'firebase-crashlytics-ktx' "$APP_DIR/build.gradle.kts" \
  && rg -q 'com.google.firebase.crashlytics' "$ANDROID_DIR/build.gradle.kts"; then
  status_line "PASS" "Crashlytics dependency" "Crashlytics dependency and plugin declaration are configured."
else
  status_line "BLOCKED" "Crashlytics dependency" "Crashlytics dependency/plugin configuration missing."
fi

if [[ -f "$ROOT_DIR/backend/app/resources/welfare_policy_countries.json" ]]; then
  if rg -q '"ute_tray_policy_by_country"' "$ROOT_DIR/backend/app/resources/welfare_policy_countries.json"; then
    status_line "PASS" "Welfare country policy config" "Country-aware ute/truck transport policy config is present."
  else
    status_line "BLOCKED" "Welfare country policy config" "Config file exists but missing \`ute_tray_policy_by_country\` mapping."
  fi
else
  status_line "BLOCKED" "Welfare country policy config" "Missing \`backend/app/resources/welfare_policy_countries.json\`."
fi

if rg -q 'def _crate_policy_guard' "$ROOT_DIR/backend/app/services/ai_orchestrator.py" \
  && rg -q 'def _welfare_policy_guard' "$ROOT_DIR/backend/app/services/ai_orchestrator.py" \
  && rg -q 'def _resolve_country_code' "$ROOT_DIR/backend/app/services/ai_orchestrator.py"; then
  status_line "PASS" "BarkWise policy guards" "Crating/welfare/country policy guard methods detected in orchestrator."
else
  status_line "BLOCKED" "BarkWise policy guards" "Missing one or more policy guard methods in \`ai_orchestrator.py\`."
fi

if [[ "$RUN_BACKEND_POLICY_TESTS" == "1" ]]; then
  if [[ -x "$BACKEND_PYTHON" ]]; then
    if "$BACKEND_PYTHON" -m pytest "$ROOT_DIR/backend/tests/test_ai_orchestrator_rag_gate.py" -k "crate or policy or ute or tether or country" >"$policy_tests_log" 2>&1; then
      status_line "PASS" "BarkWise policy regression tests" "Policy tests passed. Log: \`$policy_tests_log\`."
    else
      status_line "BLOCKED" "BarkWise policy regression tests" "Policy tests failed. Log: \`$policy_tests_log\`."
    fi
  else
    status_line "TODO" "BarkWise policy regression tests" "Python env missing at \`$BACKEND_PYTHON\`; run policy tests manually."
  fi
else
  status_line "TODO" "BarkWise policy regression tests" "Skipped (RUN_BACKEND_POLICY_TESTS=0)."
fi

if [[ -f "$tester_csv" ]]; then
  if "$ANDROID_DIR/scripts/prepare_closed_beta_testers.sh" --input "$tester_csv" --output "$tester_import_csv" >"$tester_prepare_log" 2>&1; then
    tester_count="$(awk -F, 'NR > 1 && $1 ~ /@/ {count++} END {print count + 0}' "$tester_import_csv")"
    if [[ "$tester_count" -ge 25 && "$tester_count" -le 50 ]]; then
      status_line "PASS" "Tester cohort size" "${tester_count} tester emails validated. Play import file: \`$tester_import_csv\`."
    else
      status_line "TODO" "Tester cohort size" "${tester_count} tester emails validated; target is 25-50. Play import file: \`$tester_import_csv\`."
    fi
  else
    status_line "BLOCKED" "Tester cohort size" "Tester CSV validation failed. Fix \`$tester_csv\`. Log: \`$tester_prepare_log\`."
  fi
else
  status_line "TODO" "Tester cohort size" "Create \`$tester_csv\` with 25-50 tester emails."
fi

status_line "TODO" "Play Console closed track" "Manual: create/open closed track, upload signed AAB, complete content declarations, and roll out."
if [[ -f "$qr_signoff_csv" ]]; then
  qr_pass_count="$(awk -F, 'NR > 1 {install=toupper($5); invite=toupper($6); if (install == "PASS" && invite == "PASS") count++} END {print count + 0}' "$qr_signoff_csv")"
  qr_fail_count="$(awk -F, 'NR > 1 {install=toupper($5); invite=toupper($6); if (install == "FAIL" || invite == "FAIL") count++} END {print count + 0}' "$qr_signoff_csv")"
  if [[ "$qr_pass_count" -ge 2 ]]; then
    status_line "PASS" "Physical device QR checks" "${qr_pass_count} device sign-offs recorded with install+invite QR PASS in \`$qr_signoff_csv\`."
  elif [[ "$qr_fail_count" -gt 0 ]]; then
    status_line "BLOCKED" "Physical device QR checks" "${qr_fail_count} sign-off entries contain QR FAIL. Retest and append PASS rows in \`$qr_signoff_csv\`."
  else
    status_line "TODO" "Physical device QR checks" "Need at least 2 PASS rows (install+invite) in \`$qr_signoff_csv\`."
  fi
else
  status_line "TODO" "Physical device QR checks" "Create QR sign-off rows with \`android/scripts/record_qr_device_signoff.sh\` for at least two devices."
fi

{
  echo
  echo "## Summary"
  echo
  echo "- PASS: ${PASS_COUNT}"
  echo "- BLOCKED: ${BLOCKED_COUNT}"
  echo "- TODO: ${TODO_COUNT}"
  echo
  echo "## Suggested Next Action"
  if [[ "$BLOCKED_COUNT" -gt 0 ]]; then
    echo
    echo "Resolve all BLOCKED items first, then regenerate this report."
  else
    echo
    echo "Complete TODO manual Play Console and device checks, then submit closed beta."
  fi
} >> "$REPORT_PATH"

cp "$REPORT_PATH" "$LATEST_REPORT_PATH"
echo "Closed beta readiness report: $REPORT_PATH"
echo "Latest report alias: $LATEST_REPORT_PATH"
