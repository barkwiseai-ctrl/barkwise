import importlib
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "scripts"))


def _reload(module_name: str):
    if module_name in sys.modules:
        return importlib.reload(sys.modules[module_name])
    return importlib.import_module(module_name)


def test_security_rate_limits_common_helpers(capsys):
    common = _reload("security_rate_limits_common")

    assert common.parse_positive_int("9", 3) == 9
    assert common.parse_positive_int("0", 3) == 3
    assert common.parse_positive_int("bad", 3) == 3
    assert common.parse_json_or_text('{"ok":true}') == {"ok": True}
    assert common.parse_json_or_text("plain") == "plain"

    common.print_json({"a": 1}, compact=True)
    compact_out = capsys.readouterr().out.strip()
    assert compact_out == '{"a":1}'


def test_security_rate_limits_script_snapshot_and_reset(monkeypatch, capsys):
    script = _reload("security_rate_limits")

    monkeypatch.setattr(
        script,
        "fetch_rate_limits_snapshot",
        lambda **_: (200, {"total_hits": 5}),
    )
    monkeypatch.setattr(
        script,
        "reset_rate_limits_snapshot",
        lambda **_: (200, {"status": "ok"}),
    )

    monkeypatch.setattr(
        sys,
        "argv",
        ["security_rate_limits.py", "snapshot", "--token", "tkn", "--compact"],
    )
    assert script.main() == 0
    snapshot_out = capsys.readouterr().out.strip()
    assert '"status_code":200' in snapshot_out
    assert '"total_hits":5' in snapshot_out

    monkeypatch.setattr(
        sys,
        "argv",
        ["security_rate_limits.py", "reset", "--token", "tkn", "--compact"],
    )
    assert script.main() == 0
    reset_out = capsys.readouterr().out.strip()
    assert '"status_code":200' in reset_out
    assert '"status":"ok"' in reset_out


def test_export_security_rate_limits_snapshot_writes_files(monkeypatch, tmp_path):
    script = _reload("export_security_rate_limits_snapshot")

    monkeypatch.setattr(
        script,
        "fetch_rate_limits_snapshot",
        lambda **_: (200, {"total_hits": 7, "by_surface": {"auth_login": 3}}),
    )

    output_dir = tmp_path / "snapshots"
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "export_security_rate_limits_snapshot.py",
            "--token",
            "tkn",
            "--output-dir",
            str(output_dir),
        ],
    )
    assert script.main() == 0

    latest_path = output_dir / "latest.json"
    assert latest_path.exists()
    latest_payload = json.loads(latest_path.read_text(encoding="utf-8"))
    assert latest_payload["metrics"]["total_hits"] == 7
    snapshots = list(output_dir.glob("security-rate-limits-*.json"))
    assert len(snapshots) == 1


def test_check_security_rate_limits_thresholds_alert_and_ok(monkeypatch):
    script = _reload("check_security_rate_limits_thresholds")

    monkeypatch.setattr(
        script,
        "fetch_rate_limits_snapshot",
        lambda **_: (200, {"total_hits": 11, "by_surface": {"auth_login": 8}}),
    )

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=7",
            "--compact",
        ],
    )
    assert script.main() == 1

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "20",
            "--surface-limit",
            "auth_login=9",
            "--compact",
        ],
    )
    assert script.main() == 0


def test_check_security_rate_limits_thresholds_sends_webhook_and_dedupes(monkeypatch, tmp_path):
    script = _reload("check_security_rate_limits_thresholds")

    monkeypatch.setattr(
        script,
        "fetch_rate_limits_snapshot",
        lambda **_: (200, {"total_hits": 50, "by_surface": {"auth_login": 22}}),
    )

    sent_payloads: list[dict] = []

    def fake_post(*, webhook_url: str, payload: dict, timeout_seconds: float):
        sent_payloads.append(payload)
        return True, None

    monkeypatch.setattr(script, "_post_webhook_json", fake_post)
    state_path = tmp_path / "alerts-state.json"

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=20",
            "--alert-webhook-url",
            "https://example.com/hook",
            "--alert-state-path",
            str(state_path),
            "--alert-dedupe-seconds",
            "3600",
            "--compact",
        ],
    )
    assert script.main() == 1
    assert len(sent_payloads) == 1
    assert sent_payloads[0]["kind"] == "security_rate_limit_alert"
    assert state_path.exists()

    # Re-run with same violation signature; should be deduped and skip posting.
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=20",
            "--alert-webhook-url",
            "https://example.com/hook",
            "--alert-state-path",
            str(state_path),
            "--alert-dedupe-seconds",
            "3600",
            "--compact",
        ],
    )
    assert script.main() == 1
    assert len(sent_payloads) == 1


def test_check_security_rate_limits_thresholds_webhook_kind_formats(monkeypatch, tmp_path):
    script = _reload("check_security_rate_limits_thresholds")
    monkeypatch.setattr(
        script,
        "fetch_rate_limits_snapshot",
        lambda **_: (200, {"total_hits": 50, "by_surface": {"auth_login": 22}}),
    )
    state_path = tmp_path / "alerts-state-kind.json"

    sent_payloads: list[dict] = []

    def fake_post(*, webhook_url: str, payload: dict, timeout_seconds: float):
        sent_payloads.append(payload)
        return True, None

    monkeypatch.setattr(script, "_post_webhook_json", fake_post)

    # generic payload
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=20",
            "--alert-webhook-url",
            "https://example.com/hook",
            "--alert-webhook-kind",
            "generic",
            "--alert-state-path",
            str(state_path),
            "--alert-dedupe-seconds",
            "0",
            "--compact",
        ],
    )
    assert script.main() == 1
    assert "kind" in sent_payloads[-1]

    # slack payload
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=20",
            "--alert-webhook-url",
            "https://example.com/hook",
            "--alert-webhook-kind",
            "slack",
            "--alert-state-path",
            str(state_path),
            "--alert-dedupe-seconds",
            "0",
            "--compact",
        ],
    )
    assert script.main() == 1
    assert "text" in sent_payloads[-1]
    assert "blocks" in sent_payloads[-1]

    # discord payload
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "check_security_rate_limits_thresholds.py",
            "--token",
            "tkn",
            "--total-limit",
            "10",
            "--surface-limit",
            "auth_login=20",
            "--alert-webhook-url",
            "https://example.com/hook",
            "--alert-webhook-kind",
            "discord",
            "--alert-state-path",
            str(state_path),
            "--alert-dedupe-seconds",
            "0",
            "--compact",
        ],
    )
    assert script.main() == 1
    assert "content" in sent_payloads[-1]
    assert "embeds" in sent_payloads[-1]


def test_cleanup_security_rate_limits_snapshots_prunes_old_files(monkeypatch, tmp_path):
    script = _reload("cleanup_security_rate_limits_snapshots")

    output_dir = tmp_path / "snapshots"
    output_dir.mkdir(parents=True, exist_ok=True)
    old_file = output_dir / "security-rate-limits-20240220T000000Z.json"
    recent_file = output_dir / "security-rate-limits-20260222T000000Z.json"
    latest_file = output_dir / "latest.json"
    old_file.write_text("{}", encoding="utf-8")
    recent_file.write_text("{}", encoding="utf-8")
    latest_file.write_text("{}", encoding="utf-8")

    now = datetime.now(timezone.utc)
    old_ts = (now - timedelta(days=30)).timestamp()
    recent_ts = (now - timedelta(days=2)).timestamp()
    os.utime(old_file, (old_ts, old_ts))
    os.utime(recent_file, (recent_ts, recent_ts))

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "cleanup_security_rate_limits_snapshots.py",
            "--output-dir",
            str(output_dir),
            "--retain-days",
            "14",
        ],
    )
    assert script.main() == 0
    assert not old_file.exists()
    assert recent_file.exists()
    assert latest_file.exists()


def test_reset_security_alert_state_deletes_file_and_supports_dry_run(monkeypatch, tmp_path):
    script = _reload("reset_security_alert_state")

    alert_state_path = tmp_path / "alerts-state.json"
    alert_state_path.write_text('{"last_alert_at":"2026-02-22T10:00:00Z"}', encoding="utf-8")

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "reset_security_alert_state.py",
            "--alert-state-path",
            str(alert_state_path),
            "--dry-run",
            "--compact",
        ],
    )
    assert script.main() == 0
    assert alert_state_path.exists()

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "reset_security_alert_state.py",
            "--alert-state-path",
            str(alert_state_path),
            "--compact",
        ],
    )
    assert script.main() == 0
    assert not alert_state_path.exists()
