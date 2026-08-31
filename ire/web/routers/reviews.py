from __future__ import annotations

from fastapi import APIRouter, Depends, Form, Query, Request
from fastapi.templating import Jinja2Templates

from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import list_reviews, present_review_detail

router = APIRouter(tags=["reviews"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


def _review_detail_response(request: Request, task_id: str, runtime: WebRuntime, message: str | None = None):
    detail = runtime.show_review_task_fn(task_id, runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "reviews/detail.html",
        {"review": present_review_detail(detail, runtime.repo), "message": message, "current_path": request.url.path},
    )


@router.get("/reviews")
def review_list(
    request: Request,
    status: str | None = Query(default=None),
    source_system: str | None = Query(default=None),
    min_confidence: float | None = Query(default=None),
    max_confidence: float | None = Query(default=None),
    reason_code: str | None = Query(default=None),
    search: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    rows = list_reviews(
        runtime.repo,
        status=status,
        source_system=source_system,
        min_confidence=min_confidence,
        max_confidence=max_confidence,
        reason_code=reason_code,
    )
    if search:
        s = search.lower()
        rows = [
            r
            for r in rows
            if s in r["task_id"].lower()
            or s in (r["source_record_id"] or "").lower()
            or s in (r["suggested_candidate"] or "").lower()
        ]
    pending_count = sum(1 for row in rows if row["status"] == "OPEN")
    high_risk_count = sum(1 for row in rows if row.get("safety_flags"))
    return _templates(request).TemplateResponse(
        request,
        "reviews/list.html",
        {
            "reviews": rows,
            "pending_count": pending_count,
            "high_risk_count": high_risk_count,
            "filters": {
                "status": status or "",
                "source_system": source_system or "",
                "min_confidence": "" if min_confidence is None else min_confidence,
                "max_confidence": "" if max_confidence is None else max_confidence,
                "reason_code": reason_code or "",
                "search": search or "",
            },
            "current_path": request.url.path,
        },
    )


@router.get("/reviews/{task_id}")
def review_detail(request: Request, task_id: str, message: str | None = Query(default=None), runtime: WebRuntime = Depends(get_runtime)):
    return _review_detail_response(request, task_id, runtime, message=message)


@router.post("/reviews/{task_id}/approve")
def approve(
    request: Request,
    task_id: str,
    reviewer: str = Form(...),
    selected_golden_record_id: str = Form(...),
    notes: str = Form(default=""),
    runtime: WebRuntime = Depends(get_runtime),
):
    runtime.approve_review_fn(task_id, selected_golden_record_id, reviewer, notes or None, runtime.repo, runtime.config)
    return _review_detail_response(request, task_id, runtime, message="Review approved")


@router.post("/reviews/{task_id}/reject")
def reject(
    request: Request,
    task_id: str,
    reviewer: str = Form(...),
    action: str = Form(...),
    notes: str = Form(default=""),
    runtime: WebRuntime = Depends(get_runtime),
):
    runtime.reject_review_fn(task_id, action, reviewer, notes or None, runtime.repo, runtime.config)
    return _review_detail_response(request, task_id, runtime, message="Review updated")
