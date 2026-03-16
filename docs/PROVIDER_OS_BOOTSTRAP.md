# Provider OS Bootstrap

Last updated: 2026-03-09

## What this scaffold does

- Keeps one shared backend and database truth.
- Creates a separate installable Android app lane for Provider OS.
- Allows side-by-side install with owner app on the same phone.

## Android flavors added

- Owner lane:
  - `staging`, `prod`
- Provider lane:
  - `providerStaging`, `providerProd`

Package IDs:
- Owner staging: `com.barkwise.app.staging`
- Owner prod: `com.barkwise.app`
- Provider staging: `com.barkwise.app.provider.staging`
- Provider prod: `com.barkwise.app.provider`

Deep-link schemes:
- Owner app: `barkwise://join`
- Provider app: `barkwise-provider://join`

## Quick install (Provider OS)

From repo root:

```bash
./android/scripts/install_provider_phone.sh
```

Optional:

```bash
ENVIRONMENT=staging BASE_URL=http://127.0.0.1:8000/ ./android/scripts/install_provider_phone.sh
ENVIRONMENT=prod BASE_URL=https://api.barkwise.app/ ./android/scripts/install_provider_phone.sh
```

## Optional: split workspace for Provider OS development

Use a dedicated git worktree (separate folder, shared history):

```bash
./scripts/scaffold_provider_os_worktree.sh ../barkwise-provider-os codex/provider-os-scaffold
```

This gives you a provider-focused workspace without duplicating backend repos or data stacks.
