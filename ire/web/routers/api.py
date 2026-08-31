from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile

from ire import __version__
from ire.web.batch import parse_batch_upload
from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import (
    dashboard_snapshot,
    list_golden_records,
    list_reviews,
    present_batch_results,
    present_compare_result,
    present_golden_record,
    present_merge_preview,
    present_merge_result,
    present_override_result,
    present_process_result,
    present_review_detail,
    present_rollback_preview,
    present_rollback_result,
    present_timeline,
)
from ire.web.schemas import (
    ApproveReviewRequest,
    IdentityEnvelope,
    MergePreviewRequest,
    MergeRequest,
    PrimaryOverrideRequest,
    RejectReviewRequest,
    RollbackRequest,
)

router = APIRouter(prefix="/api/v1", tags=["api"])


@router.get("/health")
def health(runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "ire-phase-1-1-demo",
        "phase": "Phase 1.1 demo",
        "storage_type": "json-jsonl-file",
        "database": False,
        "production_ready": False,
        "version": __version__,
    }


@router.post("/identities/process")
def process_identity(payload: IdentityEnvelope, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.process_record_fn(payload.model_dump(), runtime.config, runtime.repo)
    return present_process_result(result, runtime.repo)


@router.post("/identities/preview")
def preview_identity(payload: IdentityEnvelope, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.preview_record_fn(payload.model_dump(), runtime.config, runtime.repo)
    return present_process_result(result, runtime.repo)


@router.post("/identities/batch")
async def batch_identity(file: UploadFile = File(...), runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    records = parse_batch_upload(file.filename, file.content_type, await file.read())
    results = runtime.process_batch_fn(records, runtime.config, runtime.repo)
    return present_batch_results(results, runtime.repo, total_records=len(records), filename=file.filename)


@router.get("/golden-records")
def golden_records(runtime: WebRuntime = Depends(get_runtime)) -> list[dict[str, Any]]:
    return list_golden_records(runtime.repo)


@router.get("/golden-records/compare")
def compare_golden(
    left: str = Query(..., min_length=1),
    right: str = Query(..., min_length=1),
    runtime: WebRuntime = Depends(get_runtime),
) -> dict[str, Any]:
    result = runtime.compare_golden_fn(left, right, runtime.repo)
    return present_compare_result(result)


@router.get("/golden-records/{golden_id}")
def golden_record_detail(golden_id: str, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    golden = runtime.repo.find_golden_record(golden_id)
    if golden is None:
        raise HTTPException(status_code=404, detail="Golden record not found")
    return present_golden_record(golden, runtime.repo.load_record_links())


@router.get("/reviews")
def reviews(
    status: str | None = Query(default=None),
    source_system: str | None = Query(default=None),
    min_confidence: float | None = Query(default=None, ge=0.0, le=1.0),
    max_confidence: float | None = Query(default=None, ge=0.0, le=1.0),
    reason_code: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
) -> list[dict[str, Any]]:
    return list_reviews(runtime.repo, status=status, source_system=source_system, min_confidence=min_confidence, max_confidence=max_confidence, reason_code=reason_code)


@router.get("/reviews/{task_id}")
def review_detail(task_id: str, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    detail = runtime.show_review_task_fn(task_id, runtime.repo)
    return present_review_detail(detail, runtime.repo)


@router.post("/reviews/{task_id}/approve")
def review_approve(task_id: str, payload: ApproveReviewRequest, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    golden = runtime.approve_review_fn(
        task_id,
        payload.selected_golden_record_id,
        payload.reviewer,
        payload.notes,
        runtime.repo,
        runtime.config,
    )
    return {
        "task_id": task_id,
        "decision": "APPROVE_MERGE",
        "golden_record": present_golden_record(golden, runtime.repo.load_record_links()),
    }


@router.post("/reviews/{task_id}/reject")
def review_reject(task_id: str, payload: RejectReviewRequest, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.reject_review_fn(task_id, payload.action, payload.reviewer, payload.notes, runtime.repo, runtime.config)
    return result


@router.get("/dashboard")
def dashboard_data(runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    return dashboard_snapshot(runtime.repo)


@router.post("/golden-records/{golden_id}/primary-values/{field_name}")
def override_primary(
    golden_id: str,
    field_name: str,
    payload: PrimaryOverrideRequest,
    runtime: WebRuntime = Depends(get_runtime),
) -> dict[str, Any]:
    result = runtime.override_primary_fn(
        golden_id, field_name, payload.value_id, payload.actor, payload.reason, runtime.repo
    )
    return present_override_result(result, runtime.repo)


@router.post("/golden-records/merge/preview")
def merge_preview(payload: MergePreviewRequest, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.preview_merge_fn(
        payload.survivor_id, payload.loser_id, runtime.repo, payload.proposed_selections
    )
    return present_merge_preview(result)


@router.post("/golden-records/merge")
def merge_golden(payload: MergeRequest, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.merge_golden_fn(
        payload.survivor_id,
        payload.loser_id,
        payload.actor,
        payload.reason,
        runtime.repo,
        payload.expected_survivor_version,
        payload.expected_loser_version,
        payload.proposed_selections,
    )
    return present_merge_result(result, runtime.repo)


@router.get("/golden-records/merge/{merge_id}/rollback-preview")
def merge_rollback_preview(merge_id: str, runtime: WebRuntime = Depends(get_runtime)) -> dict[str, Any]:
    result = runtime.rollback_preview_fn(merge_id, runtime.repo)
    return present_rollback_preview(result, runtime.repo)


@router.post("/golden-records/merge/{merge_id}/rollback")
def merge_rollback(
    merge_id: str,
    payload: RollbackRequest,
    runtime: WebRuntime = Depends(get_runtime),
) -> dict[str, Any]:
    result = runtime.rollback_merge_fn(merge_id, payload.actor, payload.reason, runtime.repo)
    return present_rollback_result(result, runtime.repo)


@router.get("/golden-records/{golden_id}/timeline")
def golden_timeline(
    golden_id: str,
    category: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
) -> list[dict[str, Any]]:
    entries = runtime.timeline_fn(golden_id, runtime.repo, category)
    return present_timeline(entries)
