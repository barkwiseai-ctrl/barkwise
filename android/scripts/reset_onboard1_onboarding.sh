#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Notice: reset_onboard1_onboarding.sh is deprecated; forwarding to reset_test_onboarding.sh."
exec "${SCRIPT_DIR}/reset_test_onboarding.sh" "$@"
