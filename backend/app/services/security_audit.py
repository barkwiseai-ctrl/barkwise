import json
import os
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Any

DEFAULT_METRICS_PATH = Path(__file__).resolve().parents[2] / "data" / "security_audit_metrics.json"
METRICS_PATH = Path(os.getenv("SECURITY_AUDIT_METRICS_PATH", str(DEFAULT_METRICS_PATH))).expanduser()
RATE_LIMIT_RECENT_MAX = 100


class SecurityAuditStore:
    def __init__(self, metrics_path: Path, recent_max: int = RATE_LIMIT_RECENT_MAX):
        self.metrics_path = metrics_path
        self.recent_max = recent_max
        self._lock = Lock()
        self._rate_limit_total = 0
        self._rate_limit_by_surface: dict[str, int] = {}
        self._rate_limit_recent: list[dict[str, Any]] = []
        self._load_metrics_from_disk()

    @staticmethod
    def _utc_now_iso() -> str:
        return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    @staticmethod
    def _normalize_surface(value: str) -> str:
        normalized = (value or "unknown").strip().lower()
        return normalized or "unknown"

    @staticmethod
    def _normalize_key(value: str) -> str:
        normalized = (value or "unknown").strip()
        return normalized or "unknown"

    def _load_metrics_from_disk(self) -> None:
        if not self.metrics_path.exists():
            return
        try:
            payload = json.loads(self.metrics_path.read_text(encoding="utf-8"))
        except Exception:
            return
        if not isinstance(payload, dict):
            return

        total_hits = payload.get("total_hits")
        if isinstance(total_hits, int) and total_hits >= 0:
            self._rate_limit_total = total_hits

        by_surface = payload.get("by_surface")
        if isinstance(by_surface, dict):
            cleaned: dict[str, int] = {}
            for key, value in by_surface.items():
                if isinstance(key, str) and isinstance(value, int) and value >= 0:
                    cleaned[key] = value
            self._rate_limit_by_surface = cleaned

        recent_hits = payload.get("recent_hits")
        if isinstance(recent_hits, list):
            cleaned_recent: list[dict[str, str]] = []
            for entry in recent_hits[-self.recent_max :]:
                if not isinstance(entry, dict):
                    continue
                at = entry.get("at")
                surface = entry.get("surface")
                key = entry.get("key")
                detail = entry.get("detail")
                if all(isinstance(v, str) for v in [at, surface, key, detail]):
                    cleaned_recent.append({"at": at, "surface": surface, "key": key, "detail": detail})
            self._rate_limit_recent = cleaned_recent

    def _persist_metrics_locked(self) -> None:
        payload = {
            "total_hits": self._rate_limit_total,
            "by_surface": dict(self._rate_limit_by_surface),
            "recent_hits": list(self._rate_limit_recent),
        }
        try:
            self.metrics_path.parent.mkdir(parents=True, exist_ok=True)
            self.metrics_path.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
        except Exception:
            # Persistence failures must not interrupt request handling.
            return

    def record_rate_limit_hit(self, *, surface: str, key: str, detail: str) -> None:
        normalized_surface = self._normalize_surface(surface)
        normalized_key = self._normalize_key(key)
        with self._lock:
            self._rate_limit_total += 1
            self._rate_limit_by_surface[normalized_surface] = self._rate_limit_by_surface.get(normalized_surface, 0) + 1
            self._rate_limit_recent.append(
                {
                    "at": self._utc_now_iso(),
                    "surface": normalized_surface,
                    "key": normalized_key,
                    "detail": detail,
                }
            )
            if len(self._rate_limit_recent) > self.recent_max:
                del self._rate_limit_recent[: len(self._rate_limit_recent) - self.recent_max]
            self._persist_metrics_locked()

    def rate_limit_snapshot(self) -> dict[str, Any]:
        with self._lock:
            return {
                "total_hits": self._rate_limit_total,
                "by_surface": dict(self._rate_limit_by_surface),
                "recent_hits": list(self._rate_limit_recent),
            }

    def reset_rate_limit_metrics(self) -> None:
        with self._lock:
            self._rate_limit_total = 0
            self._rate_limit_by_surface.clear()
            self._rate_limit_recent.clear()
            self._persist_metrics_locked()


_SECURITY_AUDIT_STORE = SecurityAuditStore(metrics_path=METRICS_PATH)


def record_rate_limit_hit(*, surface: str, key: str, detail: str) -> None:
    _SECURITY_AUDIT_STORE.record_rate_limit_hit(surface=surface, key=key, detail=detail)


def rate_limit_snapshot() -> dict[str, Any]:
    return _SECURITY_AUDIT_STORE.rate_limit_snapshot()


def reset_rate_limit_metrics() -> None:
    _SECURITY_AUDIT_STORE.reset_rate_limit_metrics()
