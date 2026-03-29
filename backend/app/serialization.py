import json
from datetime import datetime, timezone
from typing import Any

from fastapi.encoders import jsonable_encoder


def _encode_datetime(value: datetime) -> str:
    if value.tzinfo is None:
        return value.isoformat()
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def to_json_compatible(value: Any) -> Any:
    return jsonable_encoder(value, custom_encoder={datetime: _encode_datetime})


def dump_json(value: Any, *, ensure_ascii: bool = True) -> str:
    return json.dumps(
        to_json_compatible(value),
        separators=(",", ":"),
        ensure_ascii=ensure_ascii,
    )
