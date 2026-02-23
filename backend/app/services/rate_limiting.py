import os
from datetime import datetime, timedelta
from threading import Lock


def read_positive_int_env(name: str, default: int) -> int:
    raw = os.getenv(name, str(default)).strip()
    try:
        parsed = int(raw)
    except ValueError:
        return default
    if parsed <= 0:
        return default
    return parsed


class SlidingWindowHitStore:
    def __init__(self):
        self._history: dict[str, list[datetime]] = {}
        self._lock = Lock()

    @property
    def history(self) -> dict[str, list[datetime]]:
        return self._history

    def _prune_locked(self, key: str, window: timedelta, now: datetime) -> list[datetime]:
        existing = self._history.get(key, [])
        kept = [ts for ts in existing if now - ts <= window]
        self._history[key] = kept
        return kept

    def is_limited(self, *, key: str, window: timedelta, limit: int, now: datetime | None = None) -> bool:
        current_now = now or datetime.utcnow()
        with self._lock:
            kept = self._prune_locked(key=key, window=window, now=current_now)
            return len(kept) >= limit

    def add_hit(self, *, key: str, window: timedelta, now: datetime | None = None) -> int:
        current_now = now or datetime.utcnow()
        with self._lock:
            kept = self._prune_locked(key=key, window=window, now=current_now)
            kept.append(current_now)
            self._history[key] = kept
            return len(kept)

    def allow_and_add_hit(self, *, key: str, window: timedelta, limit: int, now: datetime | None = None) -> bool:
        current_now = now or datetime.utcnow()
        with self._lock:
            kept = self._prune_locked(key=key, window=window, now=current_now)
            if len(kept) >= limit:
                return False
            kept.append(current_now)
            self._history[key] = kept
            return True

    def reset_key(self, key: str) -> None:
        with self._lock:
            self._history.pop(key, None)
