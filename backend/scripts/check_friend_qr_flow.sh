#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://api.barkwiseai.com}"
ALT_BASE_URL="${ALT_BASE_URL:-https://barkwise-production.up.railway.app}"
USER_ISSUER="${USER_ISSUER:-user_2}"
USER_VERIFIER="${USER_VERIFIER:-user_1}"
PASSWORD="${PASSWORD:-petsocial-demo}"
CURL_CONNECT_TIMEOUT="${CURL_CONNECT_TIMEOUT:-5}"
CURL_MAX_TIME="${CURL_MAX_TIME:-25}"
CURL_RETRY="${CURL_RETRY:-3}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

json_field() {
  python3 -c '
import json
import sys

key = sys.argv[1]
payload = sys.stdin.read().strip()
if not payload:
    raise SystemExit(1)
obj = json.loads(payload)
value = obj.get(key)
if value is None:
    raise SystemExit(1)
print(value)
' "$1"
}

login_token() {
  local user_id="$1"
  local response
  response="$(curl_api -X POST "$BASE_URL/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"user_id\":\"$user_id\",\"password\":\"$PASSWORD\"}")"
  printf '%s' "$response" | json_field access_token
}

curl_api() {
  curl \
    --fail \
    --silent \
    --show-error \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_MAX_TIME" \
    --retry "$CURL_RETRY" \
    --retry-delay 1 \
    --retry-all-errors \
    "$@"
}

select_base_url() {
  if curl_api "$BASE_URL/health" >/dev/null 2>&1; then
    return
  fi
  if [[ -n "$ALT_BASE_URL" ]] && curl_api "$ALT_BASE_URL/health" >/dev/null 2>&1; then
    echo "Primary base URL unavailable, switching to fallback: $ALT_BASE_URL" >&2
    BASE_URL="$ALT_BASE_URL"
    return
  fi
  echo "Unable to reach either BASE_URL ($BASE_URL) or ALT_BASE_URL ($ALT_BASE_URL)." >&2
  exit 1
}

select_base_url

echo "Base URL: ${BASE_URL%/}"
echo "Issuer user: $USER_ISSUER"
echo "Verifier user: $USER_VERIFIER"

issuer_token="$(login_token "$USER_ISSUER")"
issue_json="$(curl_api -X POST "$BASE_URL/auth/friend-qr" -H "Authorization: Bearer $issuer_token")"
friend_token="$(printf '%s' "$issue_json" | json_field friend_token)"

verifier_token="$(login_token "$USER_VERIFIER")"
verify_json="$(curl_api -X POST "$BASE_URL/auth/friend-qr/verify" \
  -H "Authorization: Bearer $verifier_token" \
  -H 'Content-Type: application/json' \
  -d "{\"friend_token\":\"$friend_token\"}")"

self_status="$(curl --silent --show-error --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_MAX_TIME" \
  --retry "$CURL_RETRY" --retry-delay 1 --retry-all-errors \
  -o /tmp/friend_qr_self_check.json -w '%{http_code}' -X POST "$BASE_URL/auth/friend-qr/verify" \
  -H "Authorization: Bearer $issuer_token" \
  -H 'Content-Type: application/json' \
  -d "{\"friend_token\":\"$friend_token\"}")"

printf '\nIssue response:\n'
printf '%s\n' "$issue_json"

printf '\nVerify response (other user):\n'
printf '%s\n' "$verify_json"

printf '\nSelf-verify status: %s\n' "$self_status"
printf 'Self-verify body: %s\n' "$(cat /tmp/friend_qr_self_check.json)"

if [[ "$self_status" == "409" ]]; then
  printf '\nPASS: signed friend QR issue + verify behavior is correct.\n'
else
  printf '\nFAIL: expected self-verify HTTP 409, got %s\n' "$self_status" >&2
  exit 2
fi
