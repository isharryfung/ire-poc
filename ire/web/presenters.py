from __future__ import annotations

from collections import Counter
from typing import Any

from ire.enums import GoldenRecordStatus, LinkStatus, ReviewStatus
from ire.models import GoldenFieldValue, GoldenRecord, MatchRun, MergeHistoryEvent, RecordLink, SourceRecord
from ire.repository import IRERepository
from ire.review import ReviewDetail
from ire.safety import mask_email, mask_hkid, mask_phone, mask_value
from ire.service import ProcessResult


OUTCOME_STYLES = {
    "AUTO_MERGE": "success",
    "MANUAL_REVIEW": "warning",
    "CREATE_NEW_GOLDEN": "info",
    "VALIDATION_FAILED": "danger",
    "DUPLICATE": "secondary",
}


__all__ = [
    "mask_email",
    "mask_hkid",
    "mask_phone",
    "mask_value",
]


def _primary_field_value(values: list[GoldenFieldValue]) -> GoldenFieldValue | None:
    for value in values:
        if value.is_primary and value.is_active:
            return value
    return values[0] if values else None


def _present_golden_value(field_name: str, value: GoldenFieldValue) -> dict[str, Any]:
    return {
        "raw_value": mask_value(field_name, value.raw_value),
        "normalized_value": mask_value(field_name, value.normalized_value),
        "source_record_id": value.source_record_id,
        "source_system": value.source_system,
        "trust_score": value.trust_score,
        "is_primary": value.is_primary,
        "is_verified": value.is_verified,
        "manual_lock": value.manual_lock,
        "is_active": value.is_active,
        "observed_at": value.observed_at,
        "valid_from": value.valid_from,
        "valid_to": value.valid_to,
    }


def _present_evidence_item(item: dict[str, Any]) -> dict[str, Any]:
    field_name = item.get("field", "")
    presented = dict(item)
    for key in ("raw_a", "raw_b", "normalized_a", "normalized_b"):
        presented[key] = mask_value(field_name, presented.get(key))
    return presented


def present_candidate(candidate: dict[str, Any] | None) -> dict[str, Any] | None:
    if candidate is None:
        return None
    explainability = dict(candidate.get("explainability", {}))
    explainability["evidence"] = [_present_evidence_item(item) for item in explainability.get("evidence", [])]
    return {
        **candidate,
        "explainability": explainability,
    }


def present_process_result(result: ProcessResult | dict[str, Any], repo: IRERepository) -> dict[str, Any]:
    payload = result if isinstance(result, dict) else result.__dict__
    golden = repo.find_golden_record(payload.get("golden_record_id")) if payload.get("golden_record_id") else None
    best_candidate = present_candidate(payload.get("best_candidate"))
    return {
        "source_record_id": payload.get("source_record_id"),
        "decision": payload.get("outcome"),
        "outcome": payload.get("outcome"),
        "confidence": payload.get("confidence", 0.0),
        "golden_record_id": payload.get("golden_record_id"),
        "review_task_id": payload.get("review_task_id"),
        "is_duplicate": payload.get("is_duplicate", False),
        "is_revision": payload.get("is_revision", False),
        "match_run_id": payload.get("match_run_id"),
        "match_method": payload.get("match_method"),
        "match_tier": payload.get("match_tier"),
        "safety_flags": payload.get("safety_flags", []),
        "reason": payload.get("reason", ""),
        "validation_issues": payload.get("validation_issues", []),
        "best_candidate": best_candidate,
        "available_evidence": best_candidate.get("explainability", {}).get("evidence", []) if best_candidate else [],
        "best_golden_record": present_golden_record(golden, repo.load_record_links()) if golden is not None else None,
        "style": OUTCOME_STYLES.get(payload.get("outcome"), "secondary"),
    }


def present_batch_results(results: list[ProcessResult], repo: IRERepository, *, total_records: int, filename: str | None = None) -> dict[str, Any]:
    presented = [present_process_result(result, repo) for result in results]
    return {
        "filename": filename,
        "total_records": total_records,
        "processed_count": len(presented),
        "auto_merge_count": sum(1 for item in presented if item["outcome"] == "AUTO_MERGE"),
        "manual_review_count": sum(1 for item in presented if item["outcome"] == "MANUAL_REVIEW"),
        "create_new_count": sum(1 for item in presented if item["outcome"] == "CREATE_NEW_GOLDEN"),
        "validation_failure_count": sum(1 for item in presented if item["outcome"] == "VALIDATION_FAILED"),
        "results": presented,
    }


def present_golden_record(golden: GoldenRecord, links: list[RecordLink]) -> dict[str, Any]:
    primary_values: dict[str, Any] = {}
    all_known_values: dict[str, list[dict[str, Any]]] = {}
    for field_name, values in golden.fields.items():
        presented_values = [_present_golden_value(field_name, value) for value in values]
        all_known_values[field_name] = presented_values
        primary = next((value for value in presented_values if value["is_primary"] and value["is_active"]), presented_values[0] if presented_values else None)
        if primary is not None:
            primary_values[field_name] = primary
    active_links = [
        {
            "link_id": link.link_id,
            "source_record_id": link.source_record_id,
            "golden_record_id": link.golden_record_id,
            "status": link.status.value,
            "confidence": link.confidence,
            "created_at": link.created_at,
            "updated_at": link.updated_at,
        }
        for link in links
        if link.golden_record_id == golden.golden_record_id and link.status == LinkStatus.ACTIVE
    ]
    return {
        "golden_record_id": golden.golden_record_id,
        "status": golden.status.value,
        "version": golden.version,
        "created_at": golden.created_at,
        "updated_at": golden.updated_at,
        "superseded_by": golden.superseded_by,
        "is_superseded": golden.status == GoldenRecordStatus.SUPERSEDED or golden.superseded_by is not None,
        "primary_values": primary_values,
        "all_known_values": all_known_values,
        "active_record_links": active_links,
    }


def list_golden_records(repo: IRERepository) -> list[dict[str, Any]]:
    links = repo.load_record_links()
    goldens = sorted(repo.load_golden_records(), key=lambda item: item.updated_at, reverse=True)
    return [present_golden_record(golden, links) for golden in goldens]


def _review_rows(repo: IRERepository) -> list[dict[str, Any]]:
    source_records = {record.source_record_id: record for record in repo.load_source_records()}
    match_runs = {run.run_id: run for run in repo.load_match_runs()}
    rows: list[dict[str, Any]] = []
    for task in sorted(repo.load_manual_review_tasks(), key=lambda item: item.created_at, reverse=True):
        source_record = source_records.get(task.source_record_id)
        match_run = match_runs.get(task.run_id)
        best_candidate = None
        if match_run and match_run.best_candidate_id:
            best_candidate = next((candidate for candidate in match_run.candidates if candidate.candidate_id == match_run.best_candidate_id), None)
        rows.append(
            {
                "task_id": task.review_id,
                "status": task.status.value,
                "created_at": task.created_at,
                "updated_at": task.updated_at,
                "source_record_id": task.source_record_id,
                "source_system": source_record.source_system if source_record else None,
                "source_pk": source_record.source_pk if source_record else None,
                "confidence": best_candidate.score if best_candidate else 0.0,
                "reason": match_run.decision.value if match_run else task.suggested_decision,
                "reason_code": task.safety_flags[0] if task.safety_flags else None,
                "suggested_candidate": best_candidate.golden_record_id if best_candidate else None,
                "safety_flags": list(task.safety_flags),
            }
        )
    return rows


def list_reviews(
    repo: IRERepository,
    *,
    status: str | None = None,
    source_system: str | None = None,
    min_confidence: float | None = None,
    max_confidence: float | None = None,
    reason_code: str | None = None,
) -> list[dict[str, Any]]:
    rows = _review_rows(repo)
    if status:
        rows = [row for row in rows if (row["status"] or "").upper() == status.upper()]
    if source_system:
        rows = [row for row in rows if (row["source_system"] or "").upper() == source_system.upper()]
    if min_confidence is not None:
        rows = [row for row in rows if row["confidence"] >= min_confidence]
    if max_confidence is not None:
        rows = [row for row in rows if row["confidence"] <= max_confidence]
    if reason_code:
        rows = [row for row in rows if row.get("reason_code") == reason_code]
    return rows


def present_review_detail(detail: ReviewDetail, repo: IRERepository) -> dict[str, Any]:
    candidates = [present_candidate(candidate) for candidate in detail.candidates]
    best_candidate = candidates[0] if candidates else None
    links = repo.load_record_links()
    goldens_by_id = {golden.golden_record_id: present_golden_record(golden, links) for golden in repo.load_golden_records()}
    return {
        "task": detail.task.to_dict(),
        "task_id": detail.task.review_id,
        "status": detail.task.status.value,
        "is_completed": detail.task.status == ReviewStatus.CLOSED,
        "source_record": present_source_record(detail.source_record),
        "normalized": {key: mask_value(key, value) for key, value in detail.normalized.items()},
        "best_candidate": best_candidate,
        "alternate_candidates": candidates[1:],
        "candidates": candidates,
        "candidate_goldens": [goldens_by_id.get(candidate["golden_record_id"]) for candidate in candidates if candidate],
        "safety_flags": detail.safety_flags,
        "suggested_decision": detail.suggested_decision,
    }


def present_source_record(record: SourceRecord) -> dict[str, Any]:
    payload = {key: mask_value(key, value) for key, value in record.payload.items()}
    return {
        "source_record_id": record.source_record_id,
        "source_system": record.source_system,
        "source_pk": record.source_pk,
        "payload": payload,
        "ingested_at": record.ingested_at,
        "ingest_status": record.ingest_status.value,
        "supersedes_source_record_id": record.supersedes_source_record_id,
    }


def _recent_match_decisions(runs: list[MatchRun], source_records: dict[str, SourceRecord]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for run in sorted(runs, key=lambda item: item.created_at, reverse=True)[:10]:
        source_record = source_records.get(run.source_record_id)
        best_candidate = next((candidate for candidate in run.candidates if candidate.candidate_id == run.best_candidate_id), run.candidates[0] if run.candidates else None)
        rows.append(
            {
                "run_id": run.run_id,
                "created_at": run.created_at,
                "source_system": source_record.source_system if source_record else None,
                "source_pk": source_record.source_pk if source_record else None,
                "decision": run.decision.value,
                "confidence": best_candidate.score if best_candidate else 0.0,
                "golden_record_id": best_candidate.golden_record_id if best_candidate else None,
            }
        )
    return rows


def _present_merge_event(event: MergeHistoryEvent) -> dict[str, Any]:
    return {
        "merge_event_id": event.merge_event_id,
        "event_type": event.event_type.value,
        "winner_golden_record_id": event.winner_golden_record_id,
        "loser_golden_record_id": event.loser_golden_record_id,
        "reason": event.reason,
        "created_at": event.created_at,
        "run_id": event.run_id,
    }


def dashboard_snapshot(repo: IRERepository) -> dict[str, Any]:
    goldens = repo.load_golden_records()
    source_records = repo.load_source_records()
    reviews = repo.load_manual_review_tasks()
    runs = repo.load_match_runs()
    merge_history = repo.load_merge_history_events()
    audits = repo.load_audit_events()
    source_records_by_id = {record.source_record_id: record for record in source_records}

    decision_distribution = Counter(run.decision.value for run in runs)
    source_distribution = Counter(record.source_system for record in source_records)

    return {
        "metrics": {
            "active_golden_records": sum(1 for golden in goldens if golden.status == GoldenRecordStatus.ACTIVE),
            "source_records_processed": len(source_records),
            "auto_merges": sum(1 for run in runs if run.decision.value == "AUTO_MERGE"),
            "pending_reviews": sum(1 for review in reviews if review.status == ReviewStatus.OPEN),
            "new_golden_records": sum(1 for run in runs if run.decision.value == "CREATE_NEW_GOLDEN"),
            "validation_failures": sum(1 for audit in audits if audit.event_type == "VALIDATION_FAILED"),
        },
        "recent_match_decisions": _recent_match_decisions(runs, source_records_by_id),
        "recent_merge_history": [_present_merge_event(event) for event in sorted(merge_history, key=lambda item: item.created_at, reverse=True)[:10]],
        "decision_distribution": [{"decision": key, "count": value} for key, value in sorted(decision_distribution.items())],
        "source_system_distribution": [{"source_system": key, "count": value} for key, value in sorted(source_distribution.items())],
    }
