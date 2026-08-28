from __future__ import annotations

import json
import re
from datetime import date
from typing import Any

_HKID_RE = re.compile(r"^[A-Z]{1,2}[0-9]{6}[0-9A]$")


def _clean_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalize_hkid(v: Any) -> str | None:
    text = _clean_string(v)
    if text is None:
        return None
    normalized = text.replace(" ", "").upper()
    if not _HKID_RE.fullmatch(normalized):
        raise ValueError("invalid HKID format")
    return normalized


def normalize_emplid(v: Any) -> str | None:
    text = _clean_string(v)
    return text.upper() if text is not None else None


def normalize_student_id(v: Any) -> str | None:
    text = _clean_string(v)
    return text.upper() if text is not None else None


def normalize_alumni_id(v: Any) -> str | None:
    text = _clean_string(v)
    return text.upper() if text is not None else None


def normalize_email(v: Any) -> str | None:
    text = _clean_string(v)
    return text.lower() if text is not None else None


def normalize_phone(v: Any) -> str | None:
    text = _clean_string(v)
    if text is None:
        return None
    digits = "".join(ch for ch in text if ch.isdigit())
    return digits[-8:] if digits else None


def normalize_dob(v: Any) -> str | None:
    text = _clean_string(v)
    if text is None:
        return None
    try:
        parsed = date.fromisoformat(text)
    except ValueError as exc:
        raise ValueError("invalid date_of_birth format") from exc
    return parsed.isoformat()


def normalize_gender(v: Any) -> str | None:
    text = _clean_string(v)
    if text is None:
        return None
    normalized = text.upper()[0]
    if normalized not in {"M", "F", "O", "U"}:
        raise ValueError("invalid gender value")
    return normalized


def normalize_name(v: Any) -> str | None:
    text = _clean_string(v)
    return text.title() if text is not None else None


def normalize_address(v: Any) -> list[str]:
    text = _clean_string(v)
    if text is None:
        return []
    lowered = text.lower()
    return [token for token in re.split(r"[^a-z0-9]+", lowered) if token]


_FIELD_NORMALIZERS = {
    "hkid": normalize_hkid,
    "emplid": normalize_emplid,
    "student_id": normalize_student_id,
    "alumni_id": normalize_alumni_id,
    "email": normalize_email,
    "phone": normalize_phone,
    "date_of_birth": normalize_dob,
    "gender": normalize_gender,
    "first_name": normalize_name,
    "last_name": normalize_name,
    "full_name": normalize_name,
    "given_name": normalize_name,
    "family_name": normalize_name,
    "address": normalize_address,
}


def normalize_field(field_name: str, raw_value: Any) -> Any:
    normalizer = _FIELD_NORMALIZERS.get(field_name)
    if normalizer is None:
        text = _clean_string(raw_value)
        return text
    return normalizer(raw_value)


def canonicalize_payload(payload_dict: dict[str, Any]) -> str:
    return json.dumps(payload_dict, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
