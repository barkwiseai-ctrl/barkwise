#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"

ENVIRONMENT="${ENVIRONMENT:-staging}"
BASE_URL="${BASE_URL:-}"

case "$ENVIRONMENT" in
  staging)
    TASK=":app:installProviderStagingDebug"
    PACKAGE_NAME="com.barkwise.app.provider.staging"
    BASE_URL_ENV="BARKWISE_PROVIDER_STAGING_API_BASE_URL"
    DEFAULT_URL="http://10.0.2.2:8000/"
    ;;
  prod)
    TASK=":app:installProviderProdDebug"
    PACKAGE_NAME="com.barkwise.app.provider"
    BASE_URL_ENV="BARKWISE_PROVIDER_PROD_API_BASE_URL"
    DEFAULT_URL="https://api.barkwiseai.com/"
    ;;
  *)
    echo "Invalid ENVIRONMENT: $ENVIRONMENT (use staging|prod)"
    exit 1
    ;;
esac

TARGET_BASE_URL="$BASE_URL"
if [[ -z "$TARGET_BASE_URL" ]]; then
  TARGET_BASE_URL="$DEFAULT_URL"
fi

echo "Installing Provider OS build..."
echo "  Environment: $ENVIRONMENT"
echo "  Package:     $PACKAGE_NAME"
echo "  API base:    $TARGET_BASE_URL"

(
  cd "$ANDROID_DIR"
  env "$BASE_URL_ENV=$TARGET_BASE_URL" ./gradlew "$TASK"
)

echo "Launching $PACKAGE_NAME on physical device..."
adb -d shell am start -W -n "${PACKAGE_NAME}/com.petsocial.app.MainActivity" >/dev/null

echo "Done."
