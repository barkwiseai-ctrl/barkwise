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

Internal RAG eval (LLM mode when key is present, fallback smoke mode otherwise):

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
python3 scripts/rag_internal_eval.py --allow-fallback --json-out /tmp/rag-eval.json
```

Strict LLM-mode eval (fails fast when `OPENAI_API_KEY` is not set):

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
python3 scripts/rag_internal_eval.py --json-out /tmp/rag-eval-llm.json
```

Route telemetry summary from logs:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
python3 scripts/route_telemetry_report.py /path/to/backend.log --json-out /tmp/route-telemetry-summary.json
```

Browser beta (for iPhone Safari + desktop):

```bash
open http://localhost:8000/web/
```

- Web client is served by the backend at `/web/` (no separate frontend build needed).
- Sign in with any `user_id` and password `petsocial-demo`.
- If backend auth is optional (`AUTH_REQUIRED=false`), the app still works without login token.

Docker:

```bash
cd /Users/yingxu/public-repos/pet-social-app
docker compose up --build
```

Railway (always-on web beta for iPhone testers):

1. Deploy this repo root in Railway (it uses `/Users/yingxu/public-repos/pet-social-app/nixpacks.toml`).
2. Set env vars:
   - `AUTH_REQUIRED=true`
   - `AUTH_SECRET=<random-secret>`
   - `AUTH_TOKEN_TTL_HOURS=168`
   - `OPENAI_API_KEY=<your-key>`
   - `OPENAI_MODEL=gpt-4.1-mini`
   - `CORS_ORIGINS=https://<your-service>.up.railway.app`
   - `TRUSTED_HOSTS=<your-service>.up.railway.app,*.up.railway.app`
3. Open and share:
   - `https://<your-service>.up.railway.app/web/`

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

Share mock build by QR (Android):

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/share_mock_qr.sh
```

- Builds and packages `BarkWise Dev` (`USE_MOCK_DATA=true`) with seeded interactive data.
- Hosts a local install page and APK at `http://<your-lan-ip>:8787`.
- Prints a QR URL and also saves a local QR PNG at `/Users/yingxu/public-repos/pet-social-app/android/share/mock/qr.png` when `curl` is available.
- If your machine cannot bind `0.0.0.0` in restricted environments, set `BIND_HOST=127.0.0.1` explicitly.
- For people outside your Wi-Fi, run with a public tunnel URL:

```bash
BASE_URL="https://your-public-url.example" START_SERVER=0 SKIP_BUILD=1 ./android/scripts/share_mock_qr.sh
```

One-command public tunnel (internet-share + QR):

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/share_mock_public_tunnel.sh
```

- Auto-selects tunnel provider (`cloudflared`, then `ngrok`, then `localhost.run` via SSH).
- Prints live public landing URL + direct APK URL + QR URL.
- Saves public QR PNG to `/Users/yingxu/public-repos/pet-social-app/android/share/mock/qr-public.png`.
- Validates that both the landing page and APK URL are reachable before announcing success.
- Writes a copy-paste tester handoff note at `/Users/yingxu/public-repos/pet-social-app/android/share/mock/tester-instructions.txt`.
- Keep terminal open while people download/install.

Stable Railway installer (fixed URL + versioned APKs):

```bash
cd /Users/yingxu/public-repos/pet-social-app
SKIP_BUILD=1 ./android/scripts/publish_staging_railway_installer.sh
```

- Installer page (stable): `https://barkwise-production.up.railway.app/install/`
- Stable APK URL (always latest): `https://barkwise-production.up.railway.app/install/apk/barkwise-staging-latest.apk`
- Versioned APK URL per release: `https://barkwise-production.up.railway.app/install/apk/releases/barkwise-staging-<version>.apk`
- Release metadata:
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/web/install/apk/latest.json`
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/web/install/apk/releases.json`

Typical release flow:

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/release_preflight.sh
./android/scripts/publish_staging_railway_installer.sh
git add backend/app/web/install android/scripts/publish_staging_railway_installer.sh backend/app/main.py
git commit -m "Publish staging APK <version>"
git push
```

Optional preflight knobs:

```bash
RUN_SMOKE_HTTP=1 BASE_URL=http://localhost:8000 ./android/scripts/release_preflight.sh
RUN_ANDROID_COMPILE=0 ./android/scripts/release_preflight.sh
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
