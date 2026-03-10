#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://api.barkwiseai.com}"
USER_ISSUER="${USER_ISSUER:-user_2}"
USER_VERIFIER="${USER_VERIFIER:-user_1}"
PASSWORD="${PASSWORD:-petsocial-demo}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

json_field() {
  python3 - "$1" <<'PY'
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
PY
}

login_token() {
  local user_id="$1"
  local response
  response="$(curl -fsS -X POST "$BASE_URL/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"user_id\":\"$user_id\",\"password\":\"$PASSWORD\"}")"
  printf '%s' "$response" | json_field access_token
}

echo "Base URL: $BASE_URL"
echo "Issuer user: $USER_ISSUER"
echo "Verifier user: $USER_VERIFIER"

issuer_token="$(login_token "$USER_ISSUER")"
issue_json="$(curl -fsS -X POST "$BASE_URL/auth/friend-qr" -H "Authorization: Bearer $issuer_token")"
friend_token="$(printf '%s' "$issue_json" | json_field friend_token)"

verifier_token="$(login_token "$USER_VERIFIER")"
verify_json="$(curl -fsS -X POST "$BASE_URL/auth/friend-qr/verify" \
  -H "Authorization: Bearer $verifier_token" \
  -H 'Content-Type: application/json' \
  -d "{\"friend_token\":\"$friend_token\"}")"

self_status="$(curl -sS -o /tmp/friend_qr_self_check.json -w '%{http_code}' -X POST "$BASE_URL/auth/friend-qr/verify" \
  -H "Authorization: Bearer $issuer_token" \
  -H 'Content-Type: application/json' \
  -d "{\"friend_token\":\"$friend_token\"}")"

echo "\nIssue response:"
printf '%s\n' "$issue_json"

echo "\nVerify response (other user):"
printf '%s\n' "$verify_json"

echo "\nSelf-verify status: $self_status"
printf 'Self-verify body: %s\n' "$(cat /tmp/friend_qr_self_check.json)"

if [[ "$self_status" == "409" ]]; then
  echo "\nPASS: signed friend QR issue + verify behavior is correct."
else
  echo "\nFAIL: expected self-verify HTTP 409, got $self_status" >&2
  exit 2
fi
