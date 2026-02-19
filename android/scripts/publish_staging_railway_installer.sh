#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
INSTALL_ROOT="$ROOT_DIR/backend/app/web/install"
APK_DIR="$INSTALL_ROOT/apk"
RELEASES_DIR="$APK_DIR/releases"
STAGING_APK_SOURCE="$ANDROID_DIR/app/build/outputs/apk/staging/debug/app-staging-debug.apk"

SKIP_BUILD="${SKIP_BUILD:-0}"
INSTALL_BASE_URL="${INSTALL_BASE_URL:-https://barkwise-production.up.railway.app/install}"
VERSION="${VERSION:-}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
}

normalized_install_base() {
  local base="$1"
  base="${base%/}"
  printf "%s" "$base"
}

compute_sha256() {
  local file_path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file_path" | awk '{print $1}'
    return
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file_path" | awk '{print $1}'
    return
  fi
  echo ""
}

build_default_version() {
  local git_short="nogit"
  git_short="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
  if [[ -z "$git_short" ]]; then
    git_short="nogit"
  fi
  printf "v%s-%s" "$(date -u +%Y%m%d-%H%M%S)" "$git_short"
}

require_cmd python3
require_cmd git

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "Building staging debug APK..."
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:assembleStagingDebug
  )
fi

if [[ ! -f "$STAGING_APK_SOURCE" ]]; then
  echo "Missing staging APK at:"
  echo "  $STAGING_APK_SOURCE"
  echo "Run with SKIP_BUILD=0 or build manually via:"
  echo "  cd android && ./gradlew :app:assembleStagingDebug"
  exit 1
fi

if [[ -z "$VERSION" ]]; then
  VERSION="$(build_default_version)"
fi

mkdir -p "$RELEASES_DIR"
VERSIONED_FILENAME="barkwise-staging-${VERSION}.apk"
VERSIONED_PATH="$RELEASES_DIR/$VERSIONED_FILENAME"
LATEST_FILENAME="barkwise-staging-latest.apk"
LATEST_PATH="$APK_DIR/$LATEST_FILENAME"

cp "$STAGING_APK_SOURCE" "$VERSIONED_PATH"
cp "$STAGING_APK_SOURCE" "$LATEST_PATH"

SHA256_VALUE="$(compute_sha256 "$VERSIONED_PATH")"
PUBLISHED_AT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

python3 - "$APK_DIR" "$VERSION" "$VERSIONED_FILENAME" "$PUBLISHED_AT_UTC" "$SHA256_VALUE" <<'PY'
import json
import pathlib
import sys

apk_dir = pathlib.Path(sys.argv[1])
version = sys.argv[2]
versioned_filename = sys.argv[3]
published_at_utc = sys.argv[4]
sha256 = sys.argv[5]

latest_path = apk_dir / "latest.json"
releases_path = apk_dir / "releases.json"

latest_payload = {
    "version": version,
    "published_at_utc": published_at_utc,
    "file": "/install/apk/barkwise-staging-latest.apk",
    "sha256": sha256,
}
latest_path.write_text(json.dumps(latest_payload, indent=2) + "\n", encoding="utf-8")

releases = []
if releases_path.exists():
    try:
        existing = json.loads(releases_path.read_text(encoding="utf-8"))
        if isinstance(existing, list):
            releases = existing
    except Exception:
        releases = []

new_entry = {
    "version": version,
    "published_at_utc": published_at_utc,
    "file": f"/install/apk/releases/{versioned_filename}",
    "sha256": sha256,
}
releases = [item for item in releases if item.get("version") != version]
releases.insert(0, new_entry)
releases_path.write_text(json.dumps(releases, indent=2) + "\n", encoding="utf-8")
PY

INSTALL_BASE_URL="$(normalized_install_base "$INSTALL_BASE_URL")"
LATEST_URL="${INSTALL_BASE_URL}/apk/${LATEST_FILENAME}"
VERSIONED_URL="${INSTALL_BASE_URL}/apk/releases/${VERSIONED_FILENAME}"
PAGE_URL="${INSTALL_BASE_URL}/"

echo
echo "Railway installer artifacts updated:"
echo "Install page:      $PAGE_URL"
echo "Stable APK URL:    $LATEST_URL"
echo "Versioned APK URL: $VERSIONED_URL"
if [[ -n "$SHA256_VALUE" ]]; then
  echo "SHA-256:           $SHA256_VALUE"
fi
echo
echo "Next step:"
echo "  git add backend/app/web/install/apk android/scripts/publish_staging_railway_installer.sh backend/app/web/install backend/app/main.py"
echo "  git commit -m \"Publish staging APK ${VERSION}\""
echo "  git push"
