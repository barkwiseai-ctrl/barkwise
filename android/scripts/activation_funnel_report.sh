#!/usr/bin/env bash
set -euo pipefail

API_BASE="${BARKWISE_API_BASE:-https://barkwise-production.up.railway.app}"
WINDOW_HOURS="${WINDOW_HOURS:-72}"
REQUESTER_USER_ID="${REQUESTER_USER_ID:-}"
AUTH_TOKEN="${AUTH_TOKEN:-}"

URL="${API_BASE%/}/community/analytics/activation"

echo "Fetching activation funnel from: ${URL} (window_hours=${WINDOW_HOURS})"
if [[ -n "${REQUESTER_USER_ID}" ]]; then
  echo "Filtering requester_user_id=${REQUESTER_USER_ID}"
fi

if [[ -n "${AUTH_TOKEN}" ]]; then
  RESPONSE="$(
    curl -fsSL --get \
      --data-urlencode "window_hours=${WINDOW_HOURS}" \
      ${REQUESTER_USER_ID:+--data-urlencode "requester_user_id=${REQUESTER_USER_ID}"} \
      -H "Authorization: Bearer ${AUTH_TOKEN}" \
      "${URL}"
  )"
else
  RESPONSE="$(
    curl -fsSL --get \
      --data-urlencode "window_hours=${WINDOW_HOURS}" \
      ${REQUESTER_USER_ID:+--data-urlencode "requester_user_id=${REQUESTER_USER_ID}"} \
      "${URL}"
  )"
fi

printf '%s' "${RESPONSE}" | python3 - <<'PY'
import json
import sys

payload = json.loads(sys.stdin.read())

print("\nActivation Funnel Summary")
print("-------------------------")
print(f"window_hours: {payload.get('window_hours')}")
print(f"requester_user_id: {payload.get('requester_user_id')}")
print(f"activation_event_count: {payload.get('activation_event_count')}")
print(f"activation_diagnostic_count: {payload.get('activation_diagnostic_count')}")
print(f"unique_user_count: {payload.get('unique_user_count')}")
print(f"last_event_at: {payload.get('last_event_at')}")

print("\nBy Status")
print("---------")
for key, value in sorted((payload.get("by_status") or {}).items()):
    print(f"{key}: {value}")

print("\nBy Stage")
print("--------")
for key, value in sorted((payload.get("by_stage") or {}).items()):
    print(f"{key}: {value}")

print("\nTop Failures")
print("------------")
top_failures = payload.get("top_failures") or []
if not top_failures:
    print("none")
else:
    for item in top_failures[:10]:
        print(
            f"{item.get('created_at')} | event={item.get('event')} "
            f"| user={item.get('user_id')} | error={item.get('error')}"
        )
PY
