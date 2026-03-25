#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${1:-../barkwise-provider-os}"
BRANCH_NAME="${2:-codex/provider-os-scaffold}"

if [[ -e "$TARGET_DIR" ]]; then
  echo "Target already exists: $TARGET_DIR"
  echo "Choose a different folder or remove the existing path."
  exit 1
fi

if git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
  git -C "$ROOT_DIR" worktree add "$TARGET_DIR" "$BRANCH_NAME"
else
  git -C "$ROOT_DIR" worktree add -b "$BRANCH_NAME" "$TARGET_DIR"
fi

echo "Provider OS worktree created:"
echo "  Path:   $TARGET_DIR"
echo "  Branch: $BRANCH_NAME"
echo
echo "Next:"
echo "  1) cd $TARGET_DIR/android"
echo "  2) ./gradlew :app:installProviderStagingDebug"
echo
echo "Note: This keeps one shared backend/data truth while allowing a separate Provider OS app lane."
