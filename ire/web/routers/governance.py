from __future__ import annotations

from urllib.parse import urlencode

from fastapi import APIRouter, Depends, Form, Query, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from ire.exceptions import ValidationError
from ire.governance import (
    data_quality_summary,
    duplicate_scan,
    integrity_check,
    integrity_repair_preview,
    list_duplicate_candidates,
    show_duplicate_candidate,
    update_duplicate_candidate_status,
)
from ire.web.dependencies import WebRuntime, get_runtime

router = APIRouter(tags=["governance"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


@router.get("/duplicates")
def duplicates_list_page(
    request: Request,
    status: str | None = Query(default=None),
    severity: str | None = Query(default=None),
    search: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    rows = list_duplicate_candidates(runtime.repo, status=status, severity=severity, search=search)
    return _templates(request).TemplateResponse(
        request,
        "governance/duplicates.html",
        {
            "rows": [item.to_dict() for item in rows],
            "filters": {"status": status or "", "severity": severity or "", "search": search or ""},
            "current_path": request.url.path,
        },
    )


@router.post("/duplicates/scan")
def duplicates_scan_page(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    duplicate_scan(runtime.repo, runtime.config, include_superseded=False)
    return RedirectResponse(url="/duplicates", status_code=303)


@router.get("/duplicates/{candidate_id}")
def duplicates_detail_page(
    candidate_id: str,
    request: Request,
    message: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    candidate = show_duplicate_candidate(runtime.repo, candidate_id)
    left = runtime.repo.find_golden_record(candidate.left_golden_record_id)
    right = runtime.repo.find_golden_record(candidate.right_golden_record_id)
    return _templates(request).TemplateResponse(
        request,
        "governance/duplicate_detail.html",
        {
            "candidate": candidate.to_dict(),
            "left": left.to_dict() if left else None,
            "right": right.to_dict() if right else None,
            "message": message,
            "current_path": request.url.path,
        },
    )


@router.post("/duplicates/{candidate_id}/status")
def duplicates_update_status_page(
    candidate_id: str,
    request: Request,
    status: str = Form(...),
    actor: str = Form(default=""),
    reason: str = Form(default=""),
    runtime: WebRuntime = Depends(get_runtime),
):
    try:
        update_duplicate_candidate_status(runtime.repo, candidate_id, status, actor=actor or None, reason=reason or None)
    except ValidationError as exc:
        query = urlencode({"message": str(exc)})
        target = str(request.url_for("duplicates_detail_page", candidate_id=candidate_id))
        return RedirectResponse(url=f"{target}?{query}", status_code=303)
    query = urlencode({"message": "Status updated"})
    target = str(request.url_for("duplicates_detail_page", candidate_id=candidate_id))
    return RedirectResponse(url=f"{target}?{query}", status_code=303)


@router.get("/integrity")
def integrity_page(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    report = integrity_check(runtime.repo)
    preview = integrity_repair_preview(runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "governance/integrity.html",
        {"report": report.to_dict(), "preview": preview, "current_path": request.url.path},
    )


@router.get("/data-quality")
def data_quality_page(
    request: Request,
    source_system: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    summary = data_quality_summary(runtime.repo, runtime.config, source_system=source_system)
    return _templates(request).TemplateResponse(
        request,
        "governance/data_quality.html",
        {"summary": summary, "current_path": request.url.path},
    )


@router.get("/data-quality/sources/{source_system}")
def data_quality_source_page(
    source_system: str,
    request: Request,
    runtime: WebRuntime = Depends(get_runtime),
):
    summary = data_quality_summary(runtime.repo, runtime.config, source_system=source_system)
    return _templates(request).TemplateResponse(
        request,
        "governance/data_quality.html",
        {"summary": summary, "current_path": request.url.path},
    )
