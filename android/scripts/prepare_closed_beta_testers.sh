#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/android/share/release"
INPUT_PATH="$OUT_DIR/closed_beta_testers.csv"
OUTPUT_PATH="$OUT_DIR/closed_beta_testers_play_import.csv"
MIN_TESTERS=25
MAX_TESTERS=50
STRICT_RANGE=0

usage() {
  cat <<'EOF'
Usage:
  prepare_closed_beta_testers.sh [--input PATH] [--output PATH] [--min N] [--max N] [--strict-range]

Description:
  - Cleans tester CSV input for Play Console import.
  - Skips blank lines, comments (#...), and "email" header rows.
  - De-duplicates emails case-insensitively.
  - Validates email format.
  - Writes cleaned output with header to output CSV.

Exit codes:
  0: Success
  2: Invalid email rows present
  3: strict-range enabled and tester count is outside [min, max]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT_PATH="$2"
      shift 2
      ;;
    --output)
      OUTPUT_PATH="$2"
      shift 2
      ;;
    --min)
      MIN_TESTERS="$2"
      shift 2
      ;;
    --max)
      MAX_TESTERS="$2"
      shift 2
      ;;
    --strict-range)
      STRICT_RANGE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

[[ -f "$INPUT_PATH" ]] || { echo "Input file not found: $INPUT_PATH" >&2; exit 1; }
mkdir -p "$(dirname "$OUTPUT_PATH")"

set +e
awk_output="$(
  awk \
    -v output_path="$OUTPUT_PATH" \
    -v min_testers="$MIN_TESTERS" \
    -v max_testers="$MAX_TESTERS" \
    -v strict_range="$STRICT_RANGE" '
function trim(s) {
  sub(/^[[:space:]]+/, "", s)
  sub(/[[:space:]]+$/, "", s)
  return s
}

BEGIN {
  valid_count = 0
  invalid_count = 0
  duplicate_count = 0
}

{
  raw = $0
  gsub(/\r/, "", raw)
  line = trim(raw)

  if (line == "" || line ~ /^#/) {
    next
  }

  split(line, fields, ",")
  email = trim(fields[1])
  if (tolower(email) == "email" || email == "") {
    next
  }

  email_key = tolower(email)
  if (seen[email_key] == 1) {
    duplicate_count++
    next
  }
  seen[email_key] = 1

  if (email !~ /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/) {
    invalid_count++
    invalid_rows[invalid_count] = email
    next
  }

  valid_count++
  valid_rows[valid_count] = email
}

END {
  print "email" > output_path
  for (i = 1; i <= valid_count; i++) {
    print valid_rows[i] >> output_path
  }
  close(output_path)

  print "VALID_COUNT=" valid_count
  print "INVALID_COUNT=" invalid_count
  print "DUPLICATE_COUNT=" duplicate_count
  print "OUTPUT_PATH=" output_path

  if (invalid_count > 0) {
    for (i = 1; i <= invalid_count; i++) {
      print "INVALID_EMAIL=" invalid_rows[i] > "/dev/stderr"
    }
    exit 2
  }

  if (strict_range == 1 && (valid_count < min_testers || valid_count > max_testers)) {
    exit 3
  }
}
' "$INPUT_PATH"
)"
awk_rc=$?
set -e

printf '%s\n' "$awk_output"

if [[ "$awk_rc" -eq 2 ]]; then
  echo "Tester CSV contains invalid email rows. Fix input: $INPUT_PATH" >&2
  exit 2
fi

if [[ "$awk_rc" -eq 3 ]]; then
  echo "Tester count outside range [$MIN_TESTERS, $MAX_TESTERS] with --strict-range." >&2
  exit 3
fi

if [[ "$awk_rc" -ne 0 ]]; then
  echo "Tester CSV preparation failed." >&2
  exit "$awk_rc"
fi

