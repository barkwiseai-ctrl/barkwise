# BarkWise iOS Beta Scaffold

This is a SwiftUI iOS client scaffold for closed/open beta testing with the existing BarkWise backend.

## 1) Generate Xcode project

```bash
cd /Users/yingxu/public-repos/pet-social-app/ios/BarkWiseBeta
brew install xcodegen
xcodegen generate
```

## 2) Run on simulator/device

```bash
open BarkWiseBeta.xcodeproj
```

Run scheme `BarkWiseBeta` from Xcode.

## 3) Set backend URL/environment

Edit `/Users/yingxu/public-repos/pet-social-app/ios/BarkWiseBeta/Resources/Info.plist`:

- `API_BASE_URL`
- `APP_ENV`

Example:

```xml
<key>API_BASE_URL</key>
<string>https://your-staging-api.example.com/</string>
<key>APP_ENV</key>
<string>staging</string>
```

## 4) TestFlight prep

- In Xcode, set a unique bundle id and signing team.
- Product -> Archive.
- Upload to App Store Connect and distribute through TestFlight.

## Supported flows in scaffold

- Auth login (`/auth/login`)
- Services list (`/services/providers`)
- Community groups + join (`/community/groups`, `/community/groups/{id}/join`)
- Community posts + create lost/found (`/community/posts`)
- BarkAI chat (`/chat`)
- Notifications (`/notifications`)

This scaffold is intentionally minimal so you can iterate quickly for a real beta.
