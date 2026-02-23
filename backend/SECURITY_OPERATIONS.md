# Security Operations Runbook

This runbook covers the security rate-limit metrics APIs:

- `GET /security/rate-limits`
- `POST /security/rate-limits/reset`

## Access Model

- Requires bearer auth (`Authorization: Bearer <token>`).
- Requires actor/token match via `requester_user_id` query param.
- Requires admin user id (`admin`, `user_1`, or `user_3`).

Common failures:

- `401`: missing/invalid bearer when auth is enforced (`AUTH_REQUIRED=true`).
- `403`: token user mismatch or requester is not an admin.

## Query Metrics

Script shortcut:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/security_rate_limits.py snapshot --requester-user-id user_1
```

Raw curl:

```bash
BASE_URL="http://localhost:8000"
TOKEN="<admin-token>"
USER_ID="user_1"

curl -sS \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/security/rate-limits?requester_user_id=${USER_ID}" | jq
```

Expected response shape:

```json
{
  "total_hits": 12,
  "by_surface": {
    "auth_login": 5,
    "chat_chat": 4,
    "notifications_register_device": 3
  },
  "recent_hits": [
    {
      "at": "2026-02-22T22:00:00Z",
      "surface": "chat_chat",
      "key": "user_2",
      "detail": "chat_rate_limit_exceeded"
    }
  ]
}
```

Field notes:

- `total_hits`: cumulative count since last reset or service bootstrap restore.
- `by_surface`: per-throttle surface counters.
- `recent_hits`: latest capped event list (newest at end).

## Reset Metrics (Staging/Test)

Use reset only for controlled test windows or after incident review capture.

Script shortcut:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/security_rate_limits.py reset --requester-user-id user_1
```

Raw curl:

```bash
BASE_URL="http://localhost:8000"
TOKEN="<admin-token>"
USER_ID="user_1"

curl -sS -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/security/rate-limits/reset?requester_user_id=${USER_ID}" | jq
```

Expected success:

```json
{
  "status": "ok",
  "metrics": {
    "total_hits": 0,
    "by_surface": {},
    "recent_hits": []
  }
}
```

## Persistence

- Metrics persist to disk and survive process restart.
- Default file path:
  - `/Users/yingxu/public-repos/pet-social-app/backend/data/security_audit_metrics.json`
- Optional override:
  - `SECURITY_AUDIT_METRICS_PATH=/absolute/path/security_audit_metrics.json`

## Snapshot Export (Cron-Friendly)

One-shot export:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/export_security_rate_limits_snapshot.py --requester-user-id user_1
```

Output files:

- Timestamped snapshots:
  - `/Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/security-rate-limits-<UTC timestamp>.json`
- Latest snapshot pointer:
  - `/Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/latest.json`

Cron example (every 15 minutes):

```cron
*/15 * * * * cd /Users/yingxu/public-repos/pet-social-app/backend && AUTH_TOKEN='<admin-token>' ./scripts/export_security_rate_limits_snapshot.py --requester-user-id user_1 >> /Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/export.log 2>&1
```

Retention cleanup:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
./scripts/cleanup_security_rate_limits_snapshots.py --retain-days 14 --dry-run
./scripts/cleanup_security_rate_limits_snapshots.py --retain-days 14
```

Cron cleanup example (daily at 03:20 UTC):

```cron
20 3 * * * cd /Users/yingxu/public-repos/pet-social-app/backend && ./scripts/cleanup_security_rate_limits_snapshots.py --retain-days 14 >> /Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/export.log 2>&1
```

Single maintenance wrapper (export + cleanup):

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/run_security_rate_limits_maintenance.sh
```

Recommended single cron entry (every 15 minutes):

```cron
*/15 * * * * cd /Users/yingxu/public-repos/pet-social-app/backend && AUTH_TOKEN='<admin-token>' REQUESTER_USER_ID='user_1' RETAIN_DAYS='14' ./scripts/run_security_rate_limits_maintenance.sh >> /Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/export.log 2>&1
```

## Threshold Alert Check

Use this script to fail fast when metrics exceed thresholds:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ./scripts/check_security_rate_limits_thresholds.py \
  --requester-user-id user_1 \
  --total-limit 200 \
  --surface-limit auth_login=80 \
  --surface-limit chat_chat=100 \
  --surface-limit notifications_register_device=40
```

Webhook alert delivery (recommended):

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
AUTH_TOKEN="<admin-token>" ALERT_WEBHOOK_URL="https://hooks.slack.com/services/XXX/YYY/ZZZ" ALERT_ENV="staging" ./scripts/check_security_rate_limits_thresholds.py \
  --requester-user-id user_1 \
  --total-limit 200 \
  --surface-limit auth_login=80 \
  --surface-limit chat_chat=100 \
  --surface-limit notifications_register_device=40
```

Notes:

- Alerts are deduped by violation signature using a local state file (`alerts-state.json` by default).
- Dedupe window default is 900 seconds (`ALERT_DEDUPE_SECONDS` / `--alert-dedupe-seconds`).
- Override dedupe state file with `ALERT_STATE_PATH` when needed.
- Webhook payload mode supports:
  - `ALERT_WEBHOOK_KIND=generic` (default, structured JSON payload)
  - `ALERT_WEBHOOK_KIND=slack` (Slack-friendly payload with `text` + `blocks`)
  - `ALERT_WEBHOOK_KIND=discord` (Discord-friendly payload with `content` + `embeds`)
- Re-arm deduped alerts (for drill/retest) with:

```bash
cd /Users/yingxu/public-repos/pet-social-app/backend
./scripts/reset_security_alert_state.py --dry-run
./scripts/reset_security_alert_state.py
```

Exit codes:

- `0`: all thresholds within limits.
- `1`: one or more thresholds exceeded (alert condition).
- `2`: request/config failure (token missing, API error, invalid args).

Cron alert example (every 15 minutes):

```cron
*/15 * * * * cd /Users/yingxu/public-repos/pet-social-app/backend && AUTH_TOKEN='<admin-token>' ALERT_WEBHOOK_URL='https://hooks.slack.com/services/XXX/YYY/ZZZ' ALERT_ENV='staging' ./scripts/check_security_rate_limits_thresholds.py --requester-user-id user_1 --total-limit 200 --surface-limit auth_login=80 --surface-limit chat_chat=100 --surface-limit notifications_register_device=40 >> /Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots/alerts.log 2>&1
```

## Safe Operating Procedure

1. Fetch and store snapshot (`GET /security/rate-limits`).
2. Record why reset is needed (ticket/incident note).
3. Execute reset (`POST /security/rate-limits/reset`) only in staging/test, or after explicit production approval.
4. Re-query metrics to verify zeroed state.
5. Continue monitoring for new `429` spikes by surface.
