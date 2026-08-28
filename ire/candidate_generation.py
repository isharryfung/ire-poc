from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .config import IREConfig
from .enums import GoldenRecordStatus
from .models import GoldenRecord


@dataclass
class CandidateBlock:
    golden_record_id: str
    blocking_reasons: list[str] = field(default_factory=list)


_STRONG_FIELDS = ("hkid", "emplid", "student_id", "alumni_id", "email")


def _primary_value(record: GoldenRecord, field_name: str) -> str | None:
    for value in record.fields.get(field_name, []):
        if value.is_primary and value.is_active:
            return value.normalized_value
    return None


def _name_tokens(record: GoldenRecord) -> set[str]:
    tokens: set[str] = set()
    for field_name in ("first_name", "last_name", "full_name"):
        value = _primary_value(record, field_name)
        if value:
            tokens.update(part.lower() for part in value.split() if part)
    return tokens


def generate_candidates(
    normalized: dict[str, Any],
    golden_records: list[GoldenRecord],
    config: IREConfig,
) -> list[CandidateBlock]:
    candidate_map: dict[str, set[str]] = {}
    records = [record for record in golden_records if record.status == GoldenRecordStatus.ACTIVE]

    def add(record: GoldenRecord, reason: str) -> None:
        candidate_map.setdefault(record.golden_record_id, set()).add(reason)

    for record in records:
        for field_name in _STRONG_FIELDS:
            incoming = normalized.get(field_name)
            existing = _primary_value(record, field_name)
            if incoming and existing and incoming == existing:
                add(record, f"exact_{field_name}")

        incoming_phone = normalized.get("phone_digits") or normalized.get("phone")
        existing_phone = _primary_value(record, "phone")
        if incoming_phone and existing_phone:
            if incoming_phone == existing_phone:
                add(record, "exact_phone")
            elif incoming_phone[-8:] == existing_phone[-8:]:
                add(record, "phone_last8")

        incoming_dob = normalized.get("date_of_birth")
        record_dob = _primary_value(record, "date_of_birth")
        incoming_last = normalized.get("last_name")
        record_last = _primary_value(record, "last_name")
        if incoming_dob and record_dob and incoming_last and record_last and incoming_dob == record_dob and incoming_last == record_last:
            add(record, "last_name_dob")

        incoming_tokens = {token.lower() for token in str(normalized.get("full_name") or "").split() if token}
        record_tokens = _name_tokens(record)
        if incoming_dob and record_dob and incoming_tokens and record_tokens and incoming_dob == record_dob and incoming_tokens & record_tokens:
            add(record, "name_overlap_dob")

    if not candidate_map and len(records) <= config.matching_policy.full_scan_limit:
        for record in records:
            add(record, "full_scan_fallback")

    ordered = [
        CandidateBlock(golden_record_id=golden_id, blocking_reasons=sorted(reasons))
        for golden_id, reasons in candidate_map.items()
    ]
    ordered.sort(key=lambda item: (0 if "full_scan_fallback" not in item.blocking_reasons else 1, item.golden_record_id))
    return ordered[: config.matching_policy.candidate_limit]
