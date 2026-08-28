from __future__ import annotations

from dataclasses import dataclass, replace
from typing import Any

from .config import IREConfig
from .enums import LinkStatus, MatchDecisionType, MergeEventType, ReviewDecisionType, ReviewStatus
from .exceptions import InvalidReviewDecisionError, NotFoundError
from .ids import new_audit_event_id, new_merge_event_id, new_record_link_id, new_review_task_id, utc_now_iso
from .models import AuditEvent, GoldenRecord, ManualReviewTask, MergeHistoryEvent, RecordLink, SourceRecord
from .repository import IRERepository
from .survivorship import apply_survivorship, create_golden_record
from .validation import validate_record


@dataclass
class ReviewDetail:
    task: ManualReviewTask
    source_record: SourceRecord
    normalized: dict[str, Any]
    candidates: list[dict]
    safety_flags: list[str]
    suggested_decision: str


def create_review_task(
    source_record: SourceRecord,
    run_id: str,
    candidate_ids: list[str],
    safety_flags: list[str],
    repo: IRERepository,
) -> ManualReviewTask:
    task = ManualReviewTask(
        review_id=new_review_task_id(),
        run_id=run_id,
        source_record_id=source_record.source_record_id,
        candidate_ids=candidate_ids,
        status=ReviewStatus.OPEN,
        created_at=utc_now_iso(),
        updated_at=utc_now_iso(),
        safety_flags=safety_flags,
        suggested_decision=MatchDecisionType.MANUAL_REVIEW.value,
    )
    tasks = repo.load_manual_review_tasks()
    tasks.append(task)
    repo.save_manual_review_tasks(tasks)
    repo.append_audit_event(
        AuditEvent(
            audit_event_id=new_audit_event_id(),
            event_type="CREATE_REVIEW_TASK",
            entity_type="ManualReviewTask",
            entity_id=task.review_id,
            actor="system",
            details={"run_id": run_id, "candidate_ids": candidate_ids, "safety_flags": safety_flags},
            created_at=utc_now_iso(),
        )
    )
    return task


def list_review_tasks(status: str | None, repo: IRERepository) -> list[ManualReviewTask]:
    tasks = repo.load_manual_review_tasks()
    if status is None:
        return tasks
    normalized = status.upper()
    if normalized == "PENDING":
        normalized = ReviewStatus.OPEN.value
    return [task for task in tasks if task.status.value == normalized]


def show_review_task(review_id: str, repo: IRERepository) -> ReviewDetail:
    task = next((item for item in repo.load_manual_review_tasks() if item.review_id == review_id), None)
    if task is None:
        raise NotFoundError(f"review task not found: {review_id}")
    source_record = repo.find_source_record(task.source_record_id)
    if source_record is None:
        raise NotFoundError(f"source record not found for review: {task.source_record_id}")
    match_run = repo.find_match_run(task.run_id)
    candidates: list[dict[str, Any]] = []
    if match_run is not None:
        for candidate in match_run.candidates:
            if candidate.candidate_id in task.candidate_ids:
                candidates.append(candidate.to_dict())
    normalized = {"source_system": source_record.source_system, "source_pk": source_record.source_pk, **source_record.payload}
    return ReviewDetail(task, source_record, normalized, candidates, list(task.safety_flags), task.suggested_decision or MatchDecisionType.MANUAL_REVIEW.value)


def _get_task(review_id: str, repo: IRERepository) -> tuple[ManualReviewTask, list[ManualReviewTask]]:
    tasks = repo.load_manual_review_tasks()
    task = next((item for item in tasks if item.review_id == review_id), None)
    if task is None:
        raise NotFoundError(f"review task not found: {review_id}")
    if task.status == ReviewStatus.CLOSED:
        raise InvalidReviewDecisionError("review task already closed")
    return task, tasks


def _get_source_system(config: IREConfig, code: str):
    source_system = next((system for system in config.source_systems if system.code == code), None)
    if source_system is None:
        raise NotFoundError(f"source system not found: {code}")
    return source_system


def _save_task(tasks: list[ManualReviewTask], updated_task: ManualReviewTask, repo: IRERepository) -> None:
    replaced = [updated_task if task.review_id == updated_task.review_id else task for task in tasks]
    repo.save_manual_review_tasks(replaced)


def approve_review(
    review_id: str,
    golden_record_id: str,
    reviewer: str,
    notes: str | None,
    repo: IRERepository,
    config: IREConfig,
) -> GoldenRecord:
    task, tasks = _get_task(review_id, repo)
    source_record = repo.find_source_record(task.source_record_id)
    if source_record is None:
        raise NotFoundError(f"source record not found for review: {task.source_record_id}")
    golden = repo.find_golden_record(golden_record_id)
    if golden is None:
        raise NotFoundError(f"golden record not found: {golden_record_id}")

    source_system = _get_source_system(config, source_record.source_system)
    normalized = validate_record({"source_system": source_record.source_system, "source_pk": source_record.source_pk, "data": source_record.payload}, config).normalized
    updated_golden = apply_survivorship(golden, normalized, source_record, source_system, config)
    goldens = [updated_golden if item.golden_record_id == updated_golden.golden_record_id else item for item in repo.load_golden_records()]
    repo.save_golden_records(goldens)

    links = repo.load_record_links()
    if not any(link.source_record_id == source_record.source_record_id and link.golden_record_id == golden_record_id and link.status == LinkStatus.ACTIVE for link in links):
        links.append(RecordLink(new_record_link_id(), source_record.source_record_id, golden_record_id, LinkStatus.ACTIVE, 1.0, utc_now_iso(), utc_now_iso()))
        repo.save_record_links(links)

    closed_task = replace(task, status=ReviewStatus.CLOSED, updated_at=utc_now_iso(), assigned_to=reviewer)
    _save_task(tasks, closed_task, repo)
    repo.append_merge_history_event(MergeHistoryEvent(new_merge_event_id(), MergeEventType.MANUAL, golden_record_id, golden_record_id, notes or "manual review approval", utc_now_iso(), task.run_id))
    repo.append_audit_event(AuditEvent(new_audit_event_id(), ReviewDecisionType.APPROVE_MERGE.value, "ManualReviewTask", review_id, reviewer, {"golden_record_id": golden_record_id, "notes": notes}, utc_now_iso()))
    return updated_golden


def reject_review(
    review_id: str,
    action: str,
    reviewer: str,
    notes: str | None,
    repo: IRERepository,
    config: IREConfig,
) -> dict:
    task, tasks = _get_task(review_id, repo)
    source_record = repo.find_source_record(task.source_record_id)
    if source_record is None:
        raise NotFoundError(f"source record not found for review: {task.source_record_id}")

    result: dict[str, Any] = {"review_id": review_id, "action": action, "reviewer": reviewer}
    if action == "create-new":
        source_system = _get_source_system(config, source_record.source_system)
        normalized = validate_record({"source_system": source_record.source_system, "source_pk": source_record.source_pk, "data": source_record.payload}, config).normalized
        golden = create_golden_record(normalized, source_record, source_system, config)
        goldens = repo.load_golden_records()
        goldens.append(golden)
        repo.save_golden_records(goldens)
        links = repo.load_record_links()
        links.append(RecordLink(new_record_link_id(), source_record.source_record_id, golden.golden_record_id, LinkStatus.ACTIVE, 1.0, utc_now_iso(), utc_now_iso()))
        repo.save_record_links(links)
        repo.append_merge_history_event(MergeHistoryEvent(new_merge_event_id(), MergeEventType.CREATE_GOLDEN, golden.golden_record_id, golden.golden_record_id, notes or "manual create golden", utc_now_iso(), task.run_id))
        result["golden_record_id"] = golden.golden_record_id
    elif action != "invalid":
        raise InvalidReviewDecisionError("action must be create-new or invalid")

    closed_task = replace(task, status=ReviewStatus.CLOSED, updated_at=utc_now_iso(), assigned_to=reviewer)
    _save_task(tasks, closed_task, repo)
    repo.append_audit_event(AuditEvent(new_audit_event_id(), ReviewDecisionType.REJECT_MERGE.value, "ManualReviewTask", review_id, reviewer, {"action": action, "notes": notes}, utc_now_iso()))
    return result
