#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_PYTHON="${BACKEND_PYTHON:-$ROOT_DIR/backend/.venv/bin/python}"
SERVICE_ACCOUNT_JSON="${SERVICE_ACCOUNT_JSON:-${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:-}}"
ACCESS_TOKEN="${ACCESS_TOKEN:-${GOOGLE_PLAY_ACCESS_TOKEN:-${PLAY_ACCESS_TOKEN:-}}}"
PACKAGE_NAME="${PACKAGE_NAME:-com.barkwise.app}"

if [[ ! -x "$BACKEND_PYTHON" ]]; then
  echo "Python executable not found: $BACKEND_PYTHON" >&2
  echo "Set BACKEND_PYTHON to a python with google-auth + requests installed." >&2
  exit 2
fi

if [[ -n "$SERVICE_ACCOUNT_JSON" && ! -f "$SERVICE_ACCOUNT_JSON" ]]; then
  if [[ -n "$ACCESS_TOKEN" ]]; then
    echo "Warning: SERVICE_ACCOUNT_JSON path not found; proceeding with ACCESS_TOKEN only." >&2
    SERVICE_ACCOUNT_JSON=""
  else
    echo "Service account file not found: $SERVICE_ACCOUNT_JSON" >&2
    exit 2
  fi
fi

if [[ -z "$SERVICE_ACCOUNT_JSON" && -z "$ACCESS_TOKEN" ]]; then
  echo "Missing credentials." >&2
  echo "Provide either SERVICE_ACCOUNT_JSON or ACCESS_TOKEN." >&2
  echo "Examples:" >&2
  echo "  SERVICE_ACCOUNT_JSON=/abs/path/play-service-account.json $0" >&2
  echo "  ACCESS_TOKEN=<oauth-token> $0" >&2
  exit 2
fi

cmd=(
  "$BACKEND_PYTHON"
  "$ROOT_DIR/android/scripts/inspect_play_tracks.py"
  --package-name
  "$PACKAGE_NAME"
)

if [[ -n "$SERVICE_ACCOUNT_JSON" ]]; then
  cmd+=(--service-account "$SERVICE_ACCOUNT_JSON")
fi
if [[ -n "$ACCESS_TOKEN" ]]; then
  cmd+=(--access-token "$ACCESS_TOKEN")
fi

cmd+=("$@")
"${cmd[@]}"
