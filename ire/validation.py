from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .config import IREConfig
from .normalization import normalize_address, normalize_field, normalize_name


@dataclass
class ValidationIssue:
    field: str
    code: str
    message: str
    severity: str


@dataclass
class ValidationResult:
    valid: bool
    issues: list[ValidationIssue] = field(default_factory=list)
    normalized: dict[str, Any] = field(default_factory=dict)


def _get_source_system(config: IREConfig, code: str) -> Any:
    return next((system for system in config.source_systems if system.code == code), None)


def validate_record(raw: dict[str, Any], config: IREConfig) -> ValidationResult:
    issues: list[ValidationIssue] = []
    normalized: dict[str, Any] = {}

    for field_name in ("source_system", "source_pk", "data"):
        if field_name not in raw:
            issues.append(ValidationIssue(field_name, "REQUIRED", f"{field_name} is required", "ERROR"))

    if issues:
        return ValidationResult(valid=False, issues=issues, normalized=normalized)

    source_system_code = str(raw.get("source_system", "")).strip().upper()
    source_pk = str(raw.get("source_pk", "")).strip()
    data = raw.get("data")

    if not source_system_code:
        issues.append(ValidationIssue("source_system", "REQUIRED", "source_system is required", "ERROR"))
    if not source_pk:
        issues.append(ValidationIssue("source_pk", "REQUIRED", "source_pk is required", "ERROR"))
    if not isinstance(data, dict):
        issues.append(ValidationIssue("data", "TYPE", "data must be an object", "ERROR"))
        return ValidationResult(valid=False, issues=issues, normalized=normalized)

    source_system = _get_source_system(config, source_system_code)
    if source_system is None:
        issues.append(ValidationIssue("source_system", "UNKNOWN_SOURCE", "source_system is not configured", "ERROR"))
    elif not source_system.active:
        issues.append(ValidationIssue("source_system", "INACTIVE_SOURCE", "source_system is inactive", "ERROR"))

    normalized["source_system"] = source_system_code
    normalized["source_pk"] = source_pk

    field_map = {
        "hkid": "hkid",
        "emplid": "emplid",
        "student_id": "student_id",
        "alumni_id": "alumni_id",
        "email": "email",
        "phone": "phone",
        "date_of_birth": "date_of_birth",
        "gender": "gender",
        "first_name": "first_name",
        "last_name": "last_name",
        "full_name": "full_name",
        "given_name": "first_name",
        "family_name": "last_name",
        "address": "address",
    }

    for raw_field, normalized_field in field_map.items():
        if raw_field not in data:
            continue
        try:
            normalized_value = normalize_field(raw_field, data.get(raw_field))
        except ValueError as exc:
            issues.append(ValidationIssue(raw_field, "INVALID_FORMAT", str(exc), "ERROR"))
            continue
        if raw_field == "address":
            tokens = normalized_value
            normalized["address_tokens"] = tokens
            normalized["address"] = " ".join(tokens) if tokens else None
        elif raw_field == "phone":
            digits = "".join(ch for ch in str(data.get(raw_field)) if ch.isdigit())
            normalized["phone"] = digits or None
            normalized["phone_digits"] = digits or None
            normalized["phone_last8"] = digits[-8:] if digits else None
        else:
            normalized[normalized_field] = normalized_value

    first_name = normalized.get("first_name")
    last_name = normalized.get("last_name")
    full_name = normalized.get("full_name")
    if full_name is None and (first_name or last_name):
        normalized["full_name"] = normalize_name(" ".join(part for part in (first_name, last_name) if part))

    if normalized.get("email") and "@" not in normalized["email"]:
        issues.append(ValidationIssue("email", "INVALID_FORMAT", "email must contain @", "ERROR"))

    digits = normalized.get("phone_digits")
    if digits is not None and len(digits) < 8:
        issues.append(ValidationIssue("phone", "INVALID_FORMAT", "phone must contain at least 8 digits", "WARNING"))

    if normalized.get("address") is None and data.get("address") not in (None, ""):
        normalized["address_tokens"] = normalize_address(data.get("address"))

    valid = not any(issue.severity == "ERROR" for issue in issues)
    return ValidationResult(valid=valid, issues=issues, normalized=normalized)
