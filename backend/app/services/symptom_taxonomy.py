import json
import logging
from pathlib import Path
from typing import Any, Dict, List

logger = logging.getLogger(__name__)

RESOURCE_PATH = Path(__file__).resolve().parents[1] / "resources" / "canine_symptom_taxonomy.json"


def load_symptom_taxonomy() -> List[Dict[str, Any]]:
    if not RESOURCE_PATH.exists():
        logger.warning("Symptom taxonomy resource missing at %s", RESOURCE_PATH)
        return []
    try:
        payload = json.loads(RESOURCE_PATH.read_text(encoding="utf-8"))
    except Exception as exc:
        logger.warning("Failed to parse symptom taxonomy resource: %s", exc)
        return []
    if not isinstance(payload, list):
        logger.warning("Symptom taxonomy resource must be a JSON list: %s", RESOURCE_PATH)
        return []
    valid: List[Dict[str, Any]] = []
    for raw in payload:
        if not isinstance(raw, dict):
            continue
        entry = {
            "id": str(raw.get("id", "")).strip(),
            "label": str(raw.get("label", "")).strip(),
            "category": str(raw.get("category", "")).strip(),
            "severity": str(raw.get("severity", "monitor")).strip().lower(),
            "terms": [str(item).strip().lower() for item in raw.get("terms", []) if str(item).strip()],
            "cta_labels": [str(item).strip() for item in raw.get("cta_labels", []) if str(item).strip()],
        }
        if not entry["id"] or not entry["label"] or not entry["terms"]:
            continue
        valid.append(entry)
    return valid
