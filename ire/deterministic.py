from __future__ import annotations

from dataclasses import dataclass, field

from .candidate_generation import CandidateBlock
from .config import IREConfig
from .enums import GoldenRecordStatus, SafetyFlag
from .models import GoldenRecord, RecordLink, SourceSystem


@dataclass
class DeterministicResult:
    matched: bool
    golden_record_id: str | None
    conflict_detected: bool
    conflict_reason: str | None
    blocking_flags: list[str] = field(default_factory=list)


_STRONG_IDS = ("hkid", "emplid", "student_id", "alumni_id")


def _primary_value(record: GoldenRecord, field_name: str) -> str | None:
    for value in record.fields.get(field_name, []):
        if value.is_primary and value.is_active:
            return value.normalized_value
    return None


def check_deterministic(
    normalized: dict[str, object],
    candidates: list[CandidateBlock],
    golden_records: list[GoldenRecord],
    links: list[RecordLink],
    source_system: SourceSystem,
    config: IREConfig,
) -> DeterministicResult:
    del links, config
    active_records = {
        record.golden_record_id: record for record in golden_records if record.status == GoldenRecordStatus.ACTIVE
    }
    flags: list[str] = []

    for field_name in _STRONG_IDS:
        incoming = normalized.get(field_name)
        if not incoming:
            continue
        matches = [record.golden_record_id for record in active_records.values() if _primary_value(record, field_name) == incoming]
        if len(matches) > 1:
            return DeterministicResult(
                matched=False,
                golden_record_id=None,
                conflict_detected=True,
                conflict_reason=f"{field_name} maps to multiple golden records",
                blocking_flags=[SafetyFlag.TIER1_IDENTIFIER_CONFLICT.value],
            )

    if not source_system.internal:
        return DeterministicResult(False, None, False, None, flags)

    candidate_ids = {candidate.golden_record_id for candidate in candidates}
    for field_name in _STRONG_IDS:
        incoming = normalized.get(field_name)
        if not incoming:
            continue
        exact_hits = [record_id for record_id in candidate_ids if record_id in active_records and _primary_value(active_records[record_id], field_name) == incoming]
        if len(exact_hits) == 1:
            golden_record = active_records[exact_hits[0]]
            for other_field in _STRONG_IDS:
                if other_field == field_name:
                    continue
                incoming_other = normalized.get(other_field)
                existing_other = _primary_value(golden_record, other_field)
                if incoming_other and existing_other and incoming_other != existing_other:
                    return DeterministicResult(
                        matched=False,
                        golden_record_id=golden_record.golden_record_id,
                        conflict_detected=True,
                        conflict_reason=f"{other_field} conflict on deterministic candidate",
                        blocking_flags=[SafetyFlag.TIER1_IDENTIFIER_CONFLICT.value],
                    )
            incoming_dob = normalized.get("date_of_birth")
            existing_dob = _primary_value(golden_record, "date_of_birth")
            if incoming_dob and existing_dob and incoming_dob != existing_dob:
                return DeterministicResult(
                    matched=False,
                    golden_record_id=golden_record.golden_record_id,
                    conflict_detected=True,
                    conflict_reason="date_of_birth conflict on deterministic candidate",
                    blocking_flags=[SafetyFlag.DATE_OF_BIRTH_CONFLICT.value],
                )
            if golden_record.superseded_by or golden_record.status != GoldenRecordStatus.ACTIVE:
                flags.append(SafetyFlag.CANDIDATE_SUPERSEDED.value)
            return DeterministicResult(True, golden_record.golden_record_id, False, None, flags)

    return DeterministicResult(False, None, False, None, flags)
