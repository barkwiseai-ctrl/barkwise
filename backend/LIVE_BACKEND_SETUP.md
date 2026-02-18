# Live Backend Setup

This backend is now prepared for internet deployment with production-safe CORS/host config.

## Option: Render (quickest)

1. Push this repo to GitHub.
2. In Render, create a **Blueprint** from repository root.
3. Render will pick up `backend/render.yaml`.
4. Set secret env vars in Render:
   - `AUTH_SECRET`
   - `OPENAI_API_KEY`
5. Deploy, then verify:
   - `https://<your-render-domain>/health` returns `{"status":"ok"}`
   - `https://<your-render-domain>/ready` returns `{"status":"ready"}`

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
