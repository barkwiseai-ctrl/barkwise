# Live Backend Setup

This backend is now prepared for internet deployment with production-safe CORS/host config.

Security operations runbook:

- `/Users/yingxu/public-repos/pet-social-app/backend/SECURITY_OPERATIONS.md`

## Option: Railway (recommended for web beta sharing)

This repo already includes Railway-compatible Nixpacks config at:

- `/Users/yingxu/public-repos/pet-social-app/nixpacks.toml`

Steps:

1. Push this repo to GitHub.
2. In Railway, create a **New Project** from the GitHub repo root.
3. Railway will auto-detect `nixpacks.toml`.
4. In Railway service Variables, set:
   - `AUTH_REQUIRED=true`
   - `AUTH_SECRET=<generate-random-secret>`
   - `AUTH_TOKEN_TTL_HOURS=168`
   - `OPENAI_MODEL=gpt-4.1-mini`
   - `OPENAI_API_KEY=<your-openai-key>`
   - `BARKAI_MODE=standard`
   - `CORS_ORIGINS=https://<your-service>.up.railway.app`
   - `TRUSTED_HOSTS=<your-service>.up.railway.app,*.up.railway.app`
5. Deploy, then verify:
   - `https://<your-service>.up.railway.app/health`
   - `https://<your-service>.up.railway.app/ready`
   - `https://<your-service>.up.railway.app/web/`
   - `https://<your-service>.up.railway.app/install/`

BarkAI variant toggle:

- Keep `BARKAI_MODE=standard` for the current BarkAI behavior.
- Set `BARKAI_MODE=custom` to enable a customized BarkAI layer while keeping the same `/chat` API and Android app wiring.
- Supply the customization with `BARKAI_CUSTOM_SYSTEM_PROMPT` or `BARKAI_CUSTOM_SYSTEM_PROMPT_FILE`.
- If no custom prompt env is supplied, `custom` mode falls back to the bundled welfare-first BarkAI prompt with the hardened crate-minimization stance.
- Optional Reddit-backed custom resources:
  - `BARKAI_CUSTOM_REDDIT_QUESTION_BANK_FILE`
  - `BARKAI_CUSTOM_FORBIDDEN_PATTERNS_FILE`
- Treat Reddit as a source of common question patterns and anti-pattern mining, not as an authority source for answers.
- Verify the active mode from `/ready`, which now returns `barkai_mode`.

Notes:

- For iPhone browser testers, share only `/web/` URL.
- If you attach a custom domain, add it to both `CORS_ORIGINS` and `TRUSTED_HOSTS`.
- For Android APK distribution with a stable link:
  - publish via `/Users/yingxu/public-repos/pet-social-app/android/scripts/publish_staging_railway_installer.sh`
  - share `/install/` and `/install/apk/barkwise-staging-latest.apk`

## Option: Render (quickest)

1. Push this repo to GitHub.
2. In Render, create a **Blueprint** from repository root.
3. Render will pick up `backend/render.yaml`.
4. Set secret env vars in Render:
   - `AUTH_SECRET`
   - `OPENAI_API_KEY`
   - `BARKAI_MODE` (optional, defaults to `standard`)
   - (optional alternative) `OPENAI_API_KEY_FILE` if using mounted secret files
5. Deploy, then verify:
   - `https://<your-render-domain>/health` returns `{"status":"ok"}`
   - `https://<your-render-domain>/ready` returns `{"status":"ready","llm_configured":true,"llm_mode":"openai","barkai_mode":"standard"}`

## Post-deploy values

Use your real production domains:

- `CORS_ORIGINS`: your app origin(s), comma-separated
- `TRUSTED_HOSTS`: your API host(s), comma-separated

Example:

```text
CORS_ORIGINS=https://app.barkwise.app,https://www.barkwise.app
TRUSTED_HOSTS=api.barkwise.app,*.onrender.com
```

## Connect Android build

After backend is live, point app flavors at it:

- Dev testing:
  - set `BARKWISE_DEV_API_BASE_URL=https://<live-api-domain>/`
- Production:
  - set `BARKWISE_PROD_API_BASE_URL=https://<live-api-domain>/`

Then reinstall app.
