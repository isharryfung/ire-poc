from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile

from ire import __version__
from ire.web.batch import parse_batch_upload
from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import dashboard_snapshot, list_golden_records, list_reviews, present_batch_results, present_golden_record, present_process_result, present_review_detail
from ire.web.schemas import ApproveReviewRequest, IdentityEnvelope, RejectReviewRequest

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
