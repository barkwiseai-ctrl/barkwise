# BarkWise MVP: Major Feature Changes

Executive summary: [EXECUTIVE_SUMMARY.md](/Users/yingxu/public-repos/pet-social-app/EXECUTIVE_SUMMARY.md)

Last updated: 2026-02-18

## Major Feature Changes/Additions

- Services marketplace completed for dog walking and grooming, including category filtering, provider details, and in-chat provider listing submission.
- AI assistant flow completed with persistent chat memory, intent routing, tool-calling, emergency safety guardrails, profile suggestion card acceptance, and provider onboarding state management.
- Streaming chat support added via `POST /chat/stream` (SSE `delta` events followed by a final structured response).
- Community features completed with official plus user-created groups, nearby suburb discovery, join/apply membership flow, and lost/found post drafting.
- Retrieval-grounded responses added: BarkAI now combines trusted dog-care references with local app entities (providers, groups, posts, events) to ground answers.
- Offline mode added on Android home data loads with cached fallback and explicit retry sync controls.
- Search/sort upgrades added for services (`q` + `sort_by`) and community posts (`sort_by`) APIs.
- Auth/session hardening added with bearer token endpoints (`/auth/login`, `/auth/me`) and optional strict enforcement via `AUTH_REQUIRED=true`.
- Notification infrastructure added with user notification feed and read-state API (`/notifications`).
- Deploy-ready basics added: backend Dockerfile, root docker-compose, backend CI workflow, and API smoke tests.

## Backend Run

Local:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Docker:

```bash
cd /Users/yingxu/public-repos/pet-social-app
docker compose up --build
```

Auth hardening controls:

```bash
export AUTH_REQUIRED=true
export AUTH_SECRET="replace-this-in-prod"
export AUTH_TOKEN_TTL_HOURS=24
```

FCM push setup (backend + Android):

```bash
export FIREBASE_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
```

- Backend will automatically send push notifications for new booking/community notification events to tokens registered via `/notifications/register-device`.
- Android now attempts token sync automatically on app start and account switch.
- You still need Firebase app config on Android (`google-services.json`) for real token issuance on device.

## Android Environments

- `dev`: `BarkWise Dev` app, package suffix `.dev`, uses in-app mock API data (`USE_MOCK_DATA=true`).
- `staging`: `BarkWise (test)` app, package suffix `.staging`, uses real backend URL.
- `prod`: `BarkWise` app, no package suffix, uses real production backend URL.

Configure backend URLs in `android/local.properties` (or matching env vars):

```properties
BARKWISE_DEV_API_BASE_URL=http://10.0.2.2:8000/
BARKWISE_STAGING_API_BASE_URL=https://staging-api.barkwise.app/
BARKWISE_PROD_API_BASE_URL=https://api.barkwise.app/
```

Build examples:

```bash
cd /Users/yingxu/public-repos/pet-social-app/android
./gradlew :app:installDevDebug
./gradlew :app:installStagingDebug
./gradlew :app:installProdRelease
```

## iOS Beta Scaffold

A SwiftUI iOS beta scaffold is available at:

- `/Users/yingxu/public-repos/pet-social-app/ios/BarkWiseBeta`

Quick start:

```bash
cd /Users/yingxu/public-repos/pet-social-app/ios/BarkWiseBeta
brew install xcodegen
xcodegen generate
open BarkWiseBeta.xcodeproj
```

## Backend Live Deployment (Android Beta)

The repo includes a Render blueprint at `/Users/yingxu/public-repos/pet-social-app/render.yaml`.

1. Push this repo to GitHub.
2. In Render: `New` -> `Blueprint` -> connect this repo.
3. Render creates `barkwise-backend-staging` with HTTPS and persistent disk.
4. Set `OPENAI_API_KEY` in the Render environment.
5. Copy the Render service URL, then set:

```properties
# /Users/yingxu/public-repos/pet-social-app/android/local.properties
BARKWISE_STAGING_API_BASE_URL=https://<your-render-url>/
```
