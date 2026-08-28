from __future__ import annotations

from dataclasses import replace

from .config import IREConfig
from .enums import GoldenRecordStatus
from .ids import new_golden_record_id, utc_now_iso
from .models import GoldenFieldValue, GoldenRecord, SourceRecord, SourceSystem


_STORED_FIELDS = (
    "hkid",
    "emplid",
    "student_id",
    "alumni_id",
    "email",
    "phone",
    "first_name",
    "last_name",
    "full_name",
    "date_of_birth",
    "gender",
    "address",
)


def _new_field_value(field_name: str, normalized_value: str, source_record: SourceRecord, source_system: SourceSystem) -> GoldenFieldValue:
    raw_value = source_record.payload.get(field_name, normalized_value)
    return GoldenFieldValue(
        raw_value=str(raw_value),
        normalized_value=str(normalized_value),
        source_record_id=source_record.source_record_id,
        source_system=source_system.code,
        trust_score=source_system.trust_score,
        is_primary=False,
        is_verified=source_system.internal and field_name in {"hkid", "emplid", "student_id", "alumni_id"},
        manual_lock=False,
        is_active=True,
        observed_at=utc_now_iso(),
    )


def _rank(value: GoldenFieldValue) -> tuple:
    completeness = 1 if value.normalized_value else 0
    return (
        1 if value.manual_lock else 0,
        1 if value.is_verified else 0,
        value.trust_score,
        value.observed_at,
        completeness,
        1 if value.is_primary else 0,
    )


def _normalize_for_storage(field_name: str, incoming_normalized: dict[str, object]) -> str | None:
    if field_name == "phone":
        value = incoming_normalized.get("phone_digits") or incoming_normalized.get("phone")
    else:
        value = incoming_normalized.get(field_name)
    return None if value in (None, "", []) else str(value)


def apply_survivorship(
    golden: GoldenRecord,
    incoming_normalized: dict[str, object],
    source_record: SourceRecord,
    source_system: SourceSystem,
    config: IREConfig,
) -> GoldenRecord:
    del config
    updated_fields = {name: list(values) for name, values in golden.fields.items()}

    for field_name in _STORED_FIELDS:
        normalized_value = _normalize_for_storage(field_name, incoming_normalized)
        if normalized_value is None:
            continue

        values = list(updated_fields.get(field_name, []))
        if not any(value.normalized_value == normalized_value and value.source_record_id == source_record.source_record_id for value in values):
            values.append(_new_field_value(field_name, normalized_value, source_record, source_system))
        winner = max(values, key=_rank)
        normalized_values = [replace(value, is_primary=value is winner) for value in values]
        updated_fields[field_name] = normalized_values

    return replace(golden, fields=updated_fields, updated_at=utc_now_iso(), version=golden.version + 1, status=GoldenRecordStatus.ACTIVE)


def create_golden_record(
    normalized: dict[str, object],
    source_record: SourceRecord,
    source_system: SourceSystem,
    config: IREConfig,
) -> GoldenRecord:
    del config
    fields: dict[str, list[GoldenFieldValue]] = {}
    for field_name in _STORED_FIELDS:
        normalized_value = _normalize_for_storage(field_name, normalized)
        if normalized_value is None:
            continue
        field_value = replace(_new_field_value(field_name, normalized_value, source_record, source_system), is_primary=True)
        fields[field_name] = [field_value]

    now = utc_now_iso()
    return GoldenRecord(
        golden_record_id=new_golden_record_id(),
        status=GoldenRecordStatus.ACTIVE,
        fields=fields,
        created_at=now,
        updated_at=now,
        version=1,
    )
