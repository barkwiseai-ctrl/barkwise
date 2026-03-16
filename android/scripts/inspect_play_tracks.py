#!/usr/bin/env python3
"""Inspect Google Play release tracks for a package."""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from typing import Any

try:
    import requests
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
except ImportError as exc:  # pragma: no cover - import error is runtime environment specific
    print(
        "Missing dependency. Use backend venv or install: "
        "pip install google-auth requests",
        file=sys.stderr,
    )
    raise SystemExit(2) from exc

ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
BASE_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


@dataclass
class ApiError(RuntimeError):
    status_code: int
    message: str

    def __str__(self) -> str:
        return f"{self.status_code}: {self.message}"


def _api_request(
    method: str,
    url: str,
    headers: dict[str, str],
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    response = requests.request(method, url, headers=headers, json=payload, timeout=30)
    if response.status_code >= 400:
        message = response.text.strip()
        try:
            parsed = response.json()
            message = (
                parsed.get("error", {}).get("message")
                or parsed.get("error_description")
                or message
            )
        except ValueError:
            pass
        raise ApiError(status_code=response.status_code, message=message)
    if not response.text:
        return {}
    return response.json()


def _build_headers(service_account_path: str) -> dict[str, str]:
    creds = service_account.Credentials.from_service_account_file(
        service_account_path,
        scopes=[ANDROID_PUBLISHER_SCOPE],
    )
    creds.refresh(Request())
    if not creds.token:
        raise RuntimeError("Failed to obtain OAuth token from service account.")
    return {
        "Authorization": f"Bearer {creds.token}",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }


def _build_headers_from_access_token(access_token: str) -> dict[str, str]:
    token = access_token.strip()
    if not token:
        raise RuntimeError("Provided access token is empty.")
    if "PASTE_ACCESS_TOKEN_HERE" in token:
        raise RuntimeError(
            "Placeholder token detected. Replace ACCESS_TOKEN with a real OAuth token."
        )
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }


def _format_releases(track_name: str, releases: list[dict[str, Any]]) -> list[str]:
    if not releases:
        return [f"- {track_name}: no releases"]

    lines: list[str] = []
    for idx, release in enumerate(releases, start=1):
        status = release.get("status", "unknown")
        name = release.get("name", "")
        version_codes = ",".join(str(v) for v in release.get("versionCodes", [])) or "-"
        user_fraction = release.get("userFraction")
        fraction_label = f"{user_fraction:.2f}" if isinstance(user_fraction, float) else "-"
        in_app_update_priority = release.get("inAppUpdatePriority", "-")
        release_notes = release.get("releaseNotes", [])
        locales = ",".join(note.get("language", "") for note in release_notes if note.get("language"))
        locales_label = locales or "-"
        lines.append(
            (
                f"- {track_name} [{idx}] "
                f"status={status} "
                f"name=\"{name}\" "
                f"versionCodes={version_codes} "
                f"userFraction={fraction_label} "
                f"priority={in_app_update_priority} "
                f"notesLocales={locales_label}"
            )
        )
    return lines


def inspect_tracks(
    package_name: str,
    service_account_path: str | None = None,
    access_token: str | None = None,
) -> dict[str, Any]:
    if access_token:
        headers = _build_headers_from_access_token(access_token)
    elif service_account_path:
        headers = _build_headers(service_account_path)
    else:
        raise RuntimeError("Provide either service_account_path or access_token.")
    create_edit_url = f"{BASE_URL}/{package_name}/edits"
    edit = _api_request("POST", create_edit_url, headers=headers, payload={})
    edit_id = edit.get("id")
    if not edit_id:
        raise RuntimeError("Play API did not return an edit id.")

    tracks_url = f"{create_edit_url}/{edit_id}/tracks"
    try:
        tracks_payload = _api_request("GET", tracks_url, headers=headers)
    finally:
        # Best-effort cleanup: discard edit transaction after listing tracks.
        delete_edit_url = f"{create_edit_url}/{edit_id}"
        try:
            _api_request("DELETE", delete_edit_url, headers=headers)
        except Exception:
            pass
    return {
        "packageName": package_name,
        "editId": edit_id,
        "tracks": tracks_payload.get("tracks", []),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="List Google Play track release state for an app package."
    )
    parser.add_argument(
        "--service-account",
        default=os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip(),
        help="Path to Google Play service account JSON. "
        "Can also use GOOGLE_PLAY_SERVICE_ACCOUNT_JSON.",
    )
    parser.add_argument(
        "--access-token",
        default=(
            os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "")
            or os.environ.get("PLAY_ACCESS_TOKEN", "")
        ).strip(),
        help="OAuth access token with androidpublisher scope. "
        "Can also use GOOGLE_PLAY_ACCESS_TOKEN or PLAY_ACCESS_TOKEN.",
    )
    parser.add_argument(
        "--package-name",
        default=os.environ.get("PACKAGE_NAME", "com.barkwise.app"),
        help="Android package name (default: com.barkwise.app).",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print raw JSON response instead of human-readable summary.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    service_account_path = args.service_account.strip()
    access_token = args.access_token.strip()
    package_name = args.package_name.strip()

    if not access_token and not service_account_path:
        print(
            "Missing credentials. Provide one of:\n"
            "  --service-account /path/to/service-account.json\n"
            "  --access-token <oauth-access-token>",
            file=sys.stderr,
        )
        return 2
    if service_account_path and not os.path.isfile(service_account_path):
        print(f"Service account file not found: {service_account_path}", file=sys.stderr)
        return 2
    if not package_name:
        print("Missing --package-name.", file=sys.stderr)
        return 2

    try:
        result = inspect_tracks(
            package_name=package_name,
            service_account_path=service_account_path or None,
            access_token=access_token or None,
        )
    except ApiError as err:
        print(f"Play API request failed: {err}", file=sys.stderr)
        if err.status_code == 404:
            print(
                "Tip: verify the package name and that the service account "
                "has access to this Play Console app.",
                file=sys.stderr,
            )
        elif err.status_code in (401, 403):
            if access_token:
                print(
                    "Tip: refresh your OAuth token and retry immediately (tokens expire fast). "
                    "Ensure the token has androidpublisher scope and your signed-in account "
                    "has Play Console access to this app.",
                    file=sys.stderr,
                )
            else:
                print(
                    "Tip: grant this service account Play Console permissions "
                    "(Release manager or Admin) for the app.",
                    file=sys.stderr,
                )
        return 1
    except Exception as err:
        print(f"Unexpected error: {err}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0

    tracks = sorted(result.get("tracks", []), key=lambda t: t.get("track", ""))
    print(f"Package: {result.get('packageName')}")
    print(f"Edit ID: {result.get('editId')}")
    print(f"Tracks returned by Play API: {len(tracks)}")
    print("")

    if not tracks:
        print("No tracks returned. This usually means there are no releases yet.")
    else:
        for track in tracks:
            track_name = track.get("track", "unknown")
            releases = track.get("releases", []) or []
            for line in _format_releases(track_name=track_name, releases=releases):
                print(line)

    print("")
    print(f"Tester opt-in landing page: https://play.google.com/apps/testing/{package_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
