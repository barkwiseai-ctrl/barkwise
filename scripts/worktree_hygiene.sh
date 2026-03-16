#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---report}"

GENERATED_PATHS=(
  "android/app/build"
  "android/.gradle-local"
  "android/share/mock"
  "android/share/staging-routing"
  ".gradle-local"
  ".pytest_cache"
  "backend/.pytest_cache"
  "tmp"
)

RUNTIME_GIT_PATHS=(
  "backend/data"
  "android/share"
)

usage() {
  cat <<'EOF'
Usage:
  scripts/worktree_hygiene.sh --report
  scripts/worktree_hygiene.sh --clean

--report  Show generated-path sizes and tracked runtime/artifact files.
--clean   Delete only known-generated local paths listed in this script, then print the same report.
EOF
}

print_header() {
  echo
  echo "==> $1"
}

report_generated_sizes() {
  print_header "Generated paths"
  local found=0
  for path in "${GENERATED_PATHS[@]}"; do
    if [[ -e "$ROOT_DIR/$path" ]]; then
      du -sh "$ROOT_DIR/$path"
      found=1
    fi
  done
  if [[ "$found" == "0" ]]; then
    echo "No generated paths present."
  fi
}

report_tracked_runtime_files() {
  print_header "Tracked runtime/artifact files"
  local output=""
  output="$(git -C "$ROOT_DIR" ls-files "${RUNTIME_GIT_PATHS[@]}" 2>/dev/null | \
    rg 'backend/data/.+|android/share/.+' || true)"
  if [[ -z "$output" ]]; then
    echo "No tracked runtime/artifact files found."
    return
  fi
  echo "$output"
}

clean_generated_paths() {
  print_header "Cleaning generated paths"
  local removed=0
  for path in "${GENERATED_PATHS[@]}"; do
    if [[ -e "$ROOT_DIR/$path" ]]; then
      rm -rf "$ROOT_DIR/$path"
      echo "Removed $path"
      removed=1
    fi
  done
  if [[ "$removed" == "0" ]]; then
    echo "Nothing to remove."
  fi
}

case "$MODE" in
  --report)
    report_generated_sizes
    report_tracked_runtime_files
    ;;
  --clean)
    clean_generated_paths
    report_generated_sizes
    report_tracked_runtime_files
    ;;
  -h|--help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
