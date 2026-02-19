# Live Backend Setup

This backend is now prepared for internet deployment with production-safe CORS/host config.

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
   - `CORS_ORIGINS=https://<your-service>.up.railway.app`
   - `TRUSTED_HOSTS=<your-service>.up.railway.app,*.up.railway.app`
5. Deploy, then verify:
   - `https://<your-service>.up.railway.app/health`
   - `https://<your-service>.up.railway.app/ready`
   - `https://<your-service>.up.railway.app/web/`
   - `https://<your-service>.up.railway.app/install/`

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
   - (optional alternative) `OPENAI_API_KEY_FILE` if using mounted secret files
5. Deploy, then verify:
   - `https://<your-render-domain>/health` returns `{"status":"ok"}`
   - `https://<your-render-domain>/ready` returns `{"status":"ready","llm_configured":true,"llm_mode":"openai"}`

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
