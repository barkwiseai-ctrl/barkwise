#!/usr/bin/env python3
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Optional


@dataclass
class ApiResult:
    status_code: int
    body: Any
    latency_ms: float
    error: Optional[str] = None

    @property
    def ok(self) -> bool:
        return 200 <= self.status_code < 300 and self.error is None


class ApiClient:
    def __init__(self, base_url: str, timeout_seconds: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.token: Optional[str] = None
        self.user_id: Optional[str] = None

    def login(self, user_id: str, password: str = "petsocial-demo") -> ApiResult:
        payload = {"user_id": user_id, "password": password}
        result = self.request("POST", "/auth/login", json_body=payload)
        if result.ok and isinstance(result.body, dict):
            token = result.body.get("access_token")
            if isinstance(token, str) and token:
                self.token = token
                self.user_id = user_id
        return result

    def request(
        self,
        method: str,
        path: str,
        *,
        query: Optional[dict[str, Any]] = None,
        json_body: Optional[dict[str, Any]] = None,
        use_auth: bool = True,
    ) -> ApiResult:
        url = f"{self.base_url}{path}"
        if query:
            encoded = urllib.parse.urlencode({k: v for k, v in query.items() if v is not None})
            if encoded:
                url = f"{url}?{encoded}"

        headers = {"Accept": "application/json"}
        body_bytes = None
        if json_body is not None:
            headers["Content-Type"] = "application/json"
            body_bytes = json.dumps(json_body).encode("utf-8")
        if use_auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        req = urllib.request.Request(url=url, method=method.upper(), headers=headers, data=body_bytes)
        started = time.perf_counter()

        try:
            with urllib.request.urlopen(req, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
                latency_ms = (time.perf_counter() - started) * 1000.0
                return ApiResult(
                    status_code=response.status,
                    body=_parse_json_or_text(raw),
                    latency_ms=latency_ms,
                )
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8")
            latency_ms = (time.perf_counter() - started) * 1000.0
            return ApiResult(
                status_code=exc.code,
                body=_parse_json_or_text(raw),
                latency_ms=latency_ms,
                error=f"http_error:{exc.code}",
            )
        except urllib.error.URLError as exc:
            latency_ms = (time.perf_counter() - started) * 1000.0
            return ApiResult(
                status_code=0,
                body=None,
                latency_ms=latency_ms,
                error=f"url_error:{exc.reason}",
            )
        except Exception as exc:  # pragma: no cover - defensive branch for tooling script
            latency_ms = (time.perf_counter() - started) * 1000.0
            return ApiResult(
                status_code=0,
                body=None,
                latency_ms=latency_ms,
                error=f"request_failed:{type(exc).__name__}:{exc}",
            )


def _parse_json_or_text(raw: str) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw
