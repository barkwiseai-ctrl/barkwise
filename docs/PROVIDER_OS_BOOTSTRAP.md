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

## Local dual-phone MVP bootstrap

For the March 27 MVP pass, use the local two-phone bootstrap to install both variants, reverse local API traffic, and seed authenticated sessions:

```bash
OWNER_SERIAL=<owner-adb-serial> \
PROVIDER_SERIAL=<provider-adb-serial> \
./android/scripts/bootstrap_dual_phone_local_mvp.sh
```

Defaults:
- owner app: `com.barkwise.app.staging` signed in as `user_2`
- provider app: `com.barkwise.app.provider.staging` signed in as `user_1`
- local API base: `http://127.0.0.1:8000/`

If you only need to seed one phone after install, use:

```bash
./android/scripts/seed_phone_auth_session.sh \
  --serial <adb-serial> \
  --package com.barkwise.app.staging \
  --user-id user_2 \
  --api-base-url http://127.0.0.1:8000/ \
  --launch
```

Notes:
- The script prints `deviceLocked=` and keyguard state after seeding so you can see whether `adb` can continue autonomously.
- Devices that remain `deviceLocked=1` on Samsung always-on display still need a real unlock before cross-device UI automation can proceed.

## Optional: split workspace for Provider OS development

Use a dedicated git worktree (separate folder, shared history):

```bash
./scripts/scaffold_provider_os_worktree.sh ../barkwise-provider-os codex/provider-os-scaffold
```

This gives you a provider-focused workspace without duplicating backend repos or data stacks.
