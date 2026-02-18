#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8000}"

echo "Health"
curl -s "$BASE_URL/health"; echo

echo "Services"
curl -s "$BASE_URL/services/providers"; echo

echo "Chat"
curl -s -X POST "$BASE_URL/chat" \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"guest","message":"is my dog too fat"}'
echo

echo "Community groups"
curl -s "$BASE_URL/community/groups"; echo
