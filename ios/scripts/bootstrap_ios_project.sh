#!/usr/bin/env bash
set -euo pipefail

ROOT="/Users/yingxu/public-repos/pet-social-app/ios/BarkWiseBeta"
cd "$ROOT"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is required. Install with: brew install xcodegen"
  exit 1
fi

xcodegen generate

# XcodeGen 2.44 emits objectVersion 77 by default, which Xcode 15.4 cannot open.
# Downgrade project object version for local compatibility.
PBXPROJ="$ROOT/BarkWiseBeta.xcodeproj/project.pbxproj"
if [[ -f "$PBXPROJ" ]]; then
  sed -i '' 's/objectVersion = 77;/objectVersion = 56;/g' "$PBXPROJ"
fi

echo "Generated: $ROOT/BarkWiseBeta.xcodeproj"
