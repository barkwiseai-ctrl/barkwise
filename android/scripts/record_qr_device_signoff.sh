#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_FILE="${OUT_FILE:-$ROOT_DIR/android/share/release/physical_qr_signoff.csv}"

DEVICE_LABEL=""
ANDROID_VERSION=""
TESTER_EMAIL=""
INSTALL_QR=""
INVITE_QR=""
OTP_AUTH="NA"
MESSAGING="NA"
BOOKING="NA"
NOTES=""

usage() {
  cat <<'EOF'
Usage:
  record_qr_device_signoff.sh \
    --device "Pixel 8" \
    --android "14" \
    --tester "tester@example.com" \
    --install-qr PASS \
    --invite-qr PASS \
    [--otp PASS|FAIL|NA] \
    [--messaging PASS|FAIL|NA] \
    [--booking PASS|FAIL|NA] \
    [--notes "optional text"]

Description:
  Appends one physical-device verification row for closed beta sign-off.
EOF
}

sanitize_field() {
  local value="$1"
  value="${value//$'\n'/ }"
  value="${value//$'\r'/ }"
  value="${value//,/;}"
  printf '%s' "$value"
}

normalize_status() {
  local value="$1"
  local upper
  upper="$(printf '%s' "$value" | tr '[:lower:]' '[:upper:]')"
  case "$upper" in
    PASS|FAIL|NA)
      printf '%s' "$upper"
      ;;
    *)
      echo "Invalid status '$value'. Use PASS, FAIL, or NA." >&2
      exit 1
      ;;
  esac
}

validate_email() {
  local value="$1"
  [[ "$value" =~ ^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$ ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE_LABEL="$2"
      shift 2
      ;;
    --android)
      ANDROID_VERSION="$2"
      shift 2
      ;;
    --tester)
      TESTER_EMAIL="$2"
      shift 2
      ;;
    --install-qr)
      INSTALL_QR="$2"
      shift 2
      ;;
    --invite-qr)
      INVITE_QR="$2"
      shift 2
      ;;
    --otp)
      OTP_AUTH="$2"
      shift 2
      ;;
    --messaging)
      MESSAGING="$2"
      shift 2
      ;;
    --booking)
      BOOKING="$2"
      shift 2
      ;;
    --notes)
      NOTES="$2"
      shift 2
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

[[ -n "$DEVICE_LABEL" ]] || { echo "Missing --device" >&2; usage; exit 1; }
[[ -n "$ANDROID_VERSION" ]] || { echo "Missing --android" >&2; usage; exit 1; }
[[ -n "$TESTER_EMAIL" ]] || { echo "Missing --tester" >&2; usage; exit 1; }
[[ -n "$INSTALL_QR" ]] || { echo "Missing --install-qr" >&2; usage; exit 1; }
[[ -n "$INVITE_QR" ]] || { echo "Missing --invite-qr" >&2; usage; exit 1; }

if ! validate_email "$TESTER_EMAIL"; then
  echo "Invalid tester email: $TESTER_EMAIL" >&2
  exit 1
fi

INSTALL_QR="$(normalize_status "$INSTALL_QR")"
INVITE_QR="$(normalize_status "$INVITE_QR")"
OTP_AUTH="$(normalize_status "$OTP_AUTH")"
MESSAGING="$(normalize_status "$MESSAGING")"
BOOKING="$(normalize_status "$BOOKING")"

DEVICE_LABEL="$(sanitize_field "$DEVICE_LABEL")"
ANDROID_VERSION="$(sanitize_field "$ANDROID_VERSION")"
TESTER_EMAIL="$(sanitize_field "$TESTER_EMAIL")"
NOTES="$(sanitize_field "$NOTES")"

mkdir -p "$(dirname "$OUT_FILE")"

if [[ ! -f "$OUT_FILE" ]]; then
  echo "timestamp_utc,device_label,android_version,tester_email,install_qr,invite_qr,otp_auth,messaging,booking,notes" > "$OUT_FILE"
fi

timestamp_utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "${timestamp_utc},${DEVICE_LABEL},${ANDROID_VERSION},${TESTER_EMAIL},${INSTALL_QR},${INVITE_QR},${OTP_AUTH},${MESSAGING},${BOOKING},${NOTES}" >> "$OUT_FILE"

echo "Recorded QR sign-off row in: $OUT_FILE"

