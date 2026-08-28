from __future__ import annotations

import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from .config import IREConfig
from .enums import IngestStatus
from .ids import new_audit_event_id, new_source_record_id, utc_now_iso
from .models import AuditEvent, SourceRecord
from .normalization import canonicalize_payload
from .repository import IRERepository
from .validation import ValidationIssue, ValidationResult, validate_record


@dataclass
class IngestResult:
    source_record: SourceRecord | None
    validation_result: ValidationResult
    is_duplicate: bool
    is_revision: bool
    previous_source_record_id: str | None


def ingest_record(raw: dict, config: IREConfig, repo: IRERepository) -> IngestResult:
    validation_result = validate_record(raw, config)
    if not validation_result.valid:
        return IngestResult(None, validation_result, False, False, None)

    source_system = validation_result.normalized["source_system"]
    source_pk = validation_result.normalized["source_pk"]
    payload = dict(raw["data"])
    payload_hash = hashlib.sha256(canonicalize_payload(payload).encode()).hexdigest()

    existing = repo.find_source_record_by_payload_hash(source_system, source_pk, payload_hash)
    if existing is not None:
        return IngestResult(existing, validation_result, True, False, existing.supersedes_source_record_id)

    previous_records = repo.find_source_records_by_external_key(source_system, source_pk)
    previous_source_record_id = previous_records[-1].source_record_id if previous_records else None
    source_record = SourceRecord(
        source_record_id=new_source_record_id(),
        source_system=source_system,
        source_pk=source_pk,
        payload=payload,
        payload_hash=payload_hash,
        ingested_at=utc_now_iso(),
        ingest_status=IngestStatus.VALIDATED,
        supersedes_source_record_id=previous_source_record_id,
    )
    repo.append_source_record(source_record)
    repo.append_audit_event(
        AuditEvent(
            audit_event_id=new_audit_event_id(),
            event_type="INGEST_RECORD",
            entity_type="SourceRecord",
            entity_id=source_record.source_record_id,
            actor="system",
            details={
                "source_system": source_system,
                "source_pk": source_pk,
                "is_revision": previous_source_record_id is not None,
            },
            created_at=utc_now_iso(),
        )
    )
    return IngestResult(
        source_record=source_record,
        validation_result=validation_result,
        is_duplicate=False,
        is_revision=previous_source_record_id is not None,
        previous_source_record_id=previous_source_record_id,
    )


def _error_result(field: str, message: str) -> IngestResult:
    return IngestResult(
        source_record=None,
        validation_result=ValidationResult(
            valid=False,
            issues=[ValidationIssue(field=field, code="PARSE_ERROR", message=message, severity="ERROR")],
            normalized={},
        ),
        is_duplicate=False,
        is_revision=False,
        previous_source_record_id=None,
    )


def ingest_csv_file(csv_path: str, config: IREConfig, repo: IRERepository) -> list[IngestResult]:
    results: list[IngestResult] = []
    try:
        with Path(csv_path).open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            for row in reader:
                try:
                    source_system = row.pop("source_system")
                    source_pk = row.pop("source_pk")
                except KeyError:
                    results.append(_error_result("csv", "CSV must include source_system and source_pk columns"))
                    continue
                data = {k: v for k, v in row.items() if v not in (None, "")}
                results.append(ingest_record({"source_system": source_system, "source_pk": source_pk, "data": data}, config, repo))
    except Exception as exc:
        return [_error_result("csv", str(exc))]
    return results


def ingest_json_file(json_path: str, config: IREConfig, repo: IRERepository) -> list[IngestResult]:
    try:
        payload = json.loads(Path(json_path).read_text(encoding="utf-8"))
    except Exception as exc:
        return [_error_result("json", str(exc))]

    rows = payload if isinstance(payload, list) else [payload]
    results: list[IngestResult] = []
    for row in rows:
        if not isinstance(row, dict):
            results.append(_error_result("json", "JSON records must be objects"))
            continue
        try:
            results.append(ingest_record(row, config, repo))
        except Exception as exc:
            results.append(_error_result("json", str(exc)))
    return results
