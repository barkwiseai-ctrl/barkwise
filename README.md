# BarkWise MVP: Major Feature Changes

Executive summary: [EXECUTIVE_SUMMARY.md](/Users/yingxu/public-repos/pet-social-app/EXECUTIVE_SUMMARY.md)

Last updated: 2026-02-27

MVP + Google Play plan: [MVP_PLAY_BETA_LAUNCH_PLAN.md](/Users/yingxu/public-repos/pet-social-app/MVP_PLAY_BETA_LAUNCH_PLAN.md)

## MVP Scope Freeze (Current)

Primary scope only:
- Journey A: Meet up at a park to socialize dogs.
- Journey B: Book a groomer.

Journey A includes:
- Local group discovery.
- Park meetup event browsing and RSVP.
- Privacy-safe check-in sharing to member group only.
- Group follow-up in community surfaces.

Journey B includes:
- Groomer discovery/filtering.
- Groomer detail and availability.
- Booking request/confirmation and status tracking.

Out of scope until MVP ship:
- Feature work not directly improving Journey A or Journey B completion.
- New category expansions and non-critical social experiments.
- Net-new flows that increase complexity without improving reliability/safety.

## Major Feature Changes/Additions

- Services marketplace completed for dog walking and grooming, including category filtering, provider details, and in-chat provider listing submission.
- AI assistant flow completed with persistent chat memory, streaming chat, profile suggestion card acceptance, and provider onboarding state management.
- Streaming chat support added via `POST /chat/stream` (SSE `delta` events followed by a final structured response).
- Community features completed with official plus user-created groups, nearby suburb discovery, join/apply membership flow, and lost/found post drafting.
- Community thread interactions now include API-backed comments and replies (`GET/POST /community/posts/{post_id}/comments`).
- Offline mode added on Android home data loads with cached fallback and explicit retry sync controls.
- Search/sort upgrades added for services (`q` + `sort_by`) and community posts (`sort_by`) APIs.
- Services now include recommendation API support with dog-park/group membership suburb inference (`GET /services/recommendations`) plus inferred-suburb quote requests when suburb is omitted.
- Auth/session hardening added with bearer token endpoints (`/auth/login`, `/auth/me`) and optional strict enforcement via `AUTH_REQUIRED=true`.
- Notification infrastructure added with user notification feed and read-state API (`/notifications`).
- Deploy-ready basics added: backend Dockerfile, root docker-compose, backend CI workflow, and API smoke tests.

Security runbook:
- `/Users/yingxu/public-repos/pet-social-app/backend/SECURITY_OPERATIONS.md`

## Backend Run

Local:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Security metrics quick check:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/security_rate_limits.py snapshot --requester-user-id user_1
```

Security metrics snapshot export:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/export_security_rate_limits_snapshot.py --requester-user-id user_1
./scripts/cleanup_security_rate_limits_snapshots.py --retain-days 14 --dry-run
AUTH_TOKEN="<admin-token>" ./scripts/run_security_rate_limits_maintenance.sh
AUTH_TOKEN="<admin-token>" ./scripts/check_security_rate_limits_thresholds.py --requester-user-id user_1 --total-limit 200 --surface-limit auth_login=80 --surface-limit chat_chat=100 --surface-limit notifications_register_device=40
AUTH_TOKEN="<admin-token>" ALERT_WEBHOOK_URL="https://hooks.slack.com/services/XXX/YYY/ZZZ" ALERT_WEBHOOK_KIND="slack" ALERT_ENV="staging" ./scripts/check_security_rate_limits_thresholds.py --requester-user-id user_1 --total-limit 200 --surface-limit auth_login=80 --surface-limit chat_chat=100 --surface-limit notifications_register_device=40
./scripts/reset_security_alert_state.py --dry-run
```

Synthetic API bot scripts (Collenso Dog Park realism simulation):

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
python3 scripts/api_bots.py --base-url http://localhost:8000 --concurrency 6 --iterations 24 --json-out /tmp/api-bot-summary.json
```

- Simulates persona-driven behavior for `annika`, `snowy`, `sesame`, `pepsi`, `billie`, and `buddy`.
- Ensures and uses the group `Collenso Dog Park`, then produces photo-heavy day reports and interaction posts.
- Annika stays super active via forced photo posts (`--annika-force-posts`).
- Add `--read-only` to disable write actions.

Seed generic synthetic activity data quickly:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
python3 scripts/api_bot_seed_activity.py --base-url http://localhost:8000 --users annika,snowy,sesame,pepsi,billie,buddy
```

Run autonomous API bot loop every few hours:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
./scripts/run_api_bots_loop.sh
```

- Default interval is 3 hours (`INTERVAL_HOURS=3`).
- Defaults model Collenso-specific users and group settings:
  - `USERS=annika,snowy,sesame,pepsi,billie,buddy`
  - `COLLENSO_GROUP_NAME="Collenso Dog Park"`
  - `COLLENSO_SUBURB="Sunshine West"`
  - `COLLENSO_OWNER=annika`
  - `ANNIKA_FORCE_POSTS=2`
- Generic seeding is disabled by default for realism (`SEED_EACH_CYCLE=0`).
- Logs: `/Users/yingxu/public-repos/pet-social-app/backend/data/api-bot-loop.log`
- Per-cycle summaries: `/Users/yingxu/public-repos/pet-social-app/backend/data/api-bot-runs/summary-<timestamp>.json`
- Override behavior with env vars, for example:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
BASE_URL=http://localhost:8000 USERS=annika,snowy,sesame,pepsi,billie,buddy INTERVAL_HOURS=3 ANNIKA_FORCE_POSTS=3 COLLENSO_GROUP_NAME=\"Collenso Dog Park\" COLLENSO_SUBURB=\"Sunshine West\" CONCURRENCY=6 ITERATIONS=24 ./scripts/run_api_bots_loop.sh
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
   - `BARKAI_MODE=standard`
   - `CORS_ORIGINS=https://<your-service>.up.railway.app`
   - `TRUSTED_HOSTS=<your-service>.up.railway.app,*.up.railway.app`
3. Open and share:
   - `https://<your-service>.up.railway.app/web/`

Auth hardening controls:

```bash
export AUTH_REQUIRED=true
export AUTH_SECRET="replace-this-in-prod"
export AUTH_TOKEN_TTL_HOURS=24
export AUTH_LOGIN_FAILURE_LIMIT=8
export AUTH_LOGIN_FAILURE_WINDOW_SECONDS=600
export NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_MAX=6
export NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_WINDOW_SECONDS=300
export CHAT_RATE_LIMIT_WINDOW_SECONDS=60
export CHAT_RATE_LIMIT_MAX_REQUESTS=12
export CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS=6
export CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS=10
export BARKAI_MODE=standard
export SECURITY_AUDIT_METRICS_PATH=/absolute/path/to/security_audit_metrics.json
```

BarkAI mode switch:

- `BARKAI_MODE=standard` keeps the current BarkAI behavior.
- `BARKAI_MODE=custom` enables an additional customization prompt layer on top of the standard BarkAI prompt.
- Provide custom instructions with `BARKAI_CUSTOM_SYSTEM_PROMPT="..."` or mount a file and set `BARKAI_CUSTOM_SYSTEM_PROMPT_FILE=/absolute/path/to/barkai-custom-prompt.txt`.
- If no custom prompt env var is supplied, `custom` mode falls back to the bundled welfare-first BarkAI prompt, which includes the hardened crate-minimization policy.
- In `custom` mode, BarkAI can also read a Reddit-derived question bank from `BARKAI_CUSTOM_REDDIT_QUESTION_BANK_FILE` and a curated forbidden-reply ruleset from `BARKAI_CUSTOM_FORBIDDEN_PATTERNS_FILE`.
- Reddit forum data is used for question-pattern recognition only, not as a trusted answer source or citation source.
- Harmful Reddit answers should be reviewed by a human and distilled into explicit forbidden reply patterns, not copied directly into the model prompt.
- `/ready` now reports the active `barkai_mode`, so it is easy to confirm which variant is live before testing.

Reddit custom-mode workflow:

1. Collect dog-forum posts with the existing collector:
   - `./backend/.venv/bin/python -m collector run --config backend/configs/dogs_phase_1.json --out backend/data/reddit_dogs.jsonl --summary-out backend/data/reddit_dogs.summary.json`
2. Build BarkAI custom resources:
   - `./backend/.venv/bin/python backend/scripts/build_barkai_reddit_custom_resources.py --input backend/data/reddit_dogs.jsonl --question-bank-out backend/data/barkai_reddit_question_bank.json --bad-answer-candidates-out backend/data/barkai_bad_answer_candidates.jsonl`
3. Review `backend/data/barkai_bad_answer_candidates.jsonl` and convert the genuinely bad patterns into a curated forbidden-rules JSON.
4. Point `BARKAI_CUSTOM_REDDIT_QUESTION_BANK_FILE` and `BARKAI_CUSTOM_FORBIDDEN_PATTERNS_FILE` at those reviewed files in staging.

FCM push setup (backend + Android):

```bash
export FIREBASE_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
```

- Backend will automatically send push notifications for new booking/community notification events to tokens registered via `/notifications/register-device`.
- Android now attempts token sync automatically on app start and account switch.
- You still need Firebase app config on Android (`google-services.json`) for real token issuance on device.

## Android Environments

Owner app lane:
- `stagingDebug`: `BarkWise Test` app, package suffix `.staging`, supports switchable test capabilities.
- `prodRelease`: `BarkWise` app, no package suffix, uses the production backend URL.

Provider app lane (secondary OS shell, same backend truth):
- `providerStagingDebug`: `BW Provider` app, package suffix `.provider.staging`.
- `providerProdRelease`: `BarkWise Provider`, package suffix `.provider`.

Only these four Android variants are generated. Other flavor/build-type combinations such as `stagingRelease`, `prodDebug`, `providerStagingRelease`, and `providerProdDebug` are intentionally disabled to keep local development and release workflows simpler.

Configure backend URLs and optional test toggles in `android/local.properties` (or matching env vars):

```properties
BARKWISE_STAGING_API_BASE_URL=https://staging-api.barkwise.app/
BARKWISE_PROD_API_BASE_URL=https://api.barkwise.app/
BARKWISE_PROVIDER_STAGING_API_BASE_URL=https://staging-api.barkwise.app/
BARKWISE_PROVIDER_PROD_API_BASE_URL=https://api.barkwise.app/
BARKWISE_TEST_USE_MOCK_DATA=false
BARKWISE_TEST_ALLOW_DEMO_LOGIN=false
BARKWISE_TEST_REQUIRE_INVITE_OTP_AUTH=true
BARKWISE_TEST_ONBOARD_FAKE_SIGN_IN=false
BARKWISE_PROVIDER_TEST_USE_MOCK_DATA=false
BARKWISE_PROVIDER_TEST_ONBOARD_FAKE_SIGN_IN=false
```

Recommended test presets:

```properties
# Real backend + OTP guardrails
BARKWISE_TEST_USE_MOCK_DATA=false
BARKWISE_TEST_ALLOW_DEMO_LOGIN=false
BARKWISE_TEST_REQUIRE_INVITE_OTP_AUTH=true

# Mock-backed demo mode inside BarkWise Test
BARKWISE_TEST_USE_MOCK_DATA=true
BARKWISE_TEST_ALLOW_DEMO_LOGIN=true
BARKWISE_TEST_REQUIRE_INVITE_OTP_AUTH=false
```

Build examples:

```bash
cd /Users/yingxu/public-repos/pet-social-app/android
./gradlew :app:installStagingDebug
./gradlew :app:installProviderStagingDebug
./gradlew :app:assembleProdRelease
./gradlew :app:assembleProviderProdRelease
```

Install Provider OS on phone:

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/install_provider_phone.sh
```

Provider OS bootstrap notes:
- [docs/PROVIDER_OS_BOOTSTRAP.md](/Users/yingxu/public-repos/pet-social-app/docs/PROVIDER_OS_BOOTSTRAP.md)

Install staging build on phone against local backend (starts routing watchdog, installs, launches):

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/install_staging_local_phone.sh
```

Install staging build on phone against Railway:

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/install_staging_railway_phone.sh
```

Keep Android staging app routed to local backend (auto-heal `adb reverse`):

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/start_staging_local_routing.sh
```

- Watches device connectivity and keeps `tcp:8000 -> tcp:8000` active.
- Logs: `/Users/yingxu/public-repos/pet-social-app/android/share/staging-routing/adb-reverse-watchdog.log`
- Auto-start at macOS login (launchd):

```bash
launchctl bootstrap gui/$(id -u) /Users/yingxu/Library/LaunchAgents/com.petsocial.staging-routing-watchdog.plist
launchctl kickstart -k gui/$(id -u)/com.petsocial.staging-routing-watchdog
```

- Disable login auto-start:

```bash
launchctl bootout gui/$(id -u) /Users/yingxu/Library/LaunchAgents/com.petsocial.staging-routing-watchdog.plist
```

- Stop with:

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/stop_staging_local_routing.sh
```

Share mock build by QR (Android):

```bash
cd /Users/yingxu/public-repos/pet-social-app
./android/scripts/share_mock_qr.sh
```

- Builds and packages `BarkWise Test` with mock data and demo login enabled.
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
- Privacy policy page (for Play Console):
  - `https://<your-service>.up.railway.app/web/privacy/`
  - Source: `/Users/yingxu/public-repos/pet-social-app/backend/app/web/privacy/index.html`
- Account deletion page (for Play Console data deletion URL):
  - `https://<your-service>.up.railway.app/web/privacy/delete-account/`
  - Source: `/Users/yingxu/public-repos/pet-social-app/backend/app/web/privacy/delete-account/index.html`

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
