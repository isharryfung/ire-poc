from __future__ import annotations

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import (
    list_golden_records,
    present_compare_result,
    present_golden_record,
    present_merge_preview,
    present_rollback_preview,
    present_timeline,
)

router = APIRouter(tags=["golden"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


@router.get("/golden-records")
def golden_list(
    request: Request,
    q: str | None = Query(default=None),
    status: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    goldens = list_golden_records(runtime.repo)
    if q:
        q_lower = q.lower()
        goldens = [
            g
            for g in goldens
            if q_lower in g["golden_record_id"].lower()
            or q_lower in (g["primary_values"].get("first_name", {}).get("raw_value") or "").lower()
            or q_lower in (g["primary_values"].get("last_name", {}).get("raw_value") or "").lower()
            or q_lower in (g["primary_values"].get("email", {}).get("raw_value") or "").lower()
        ]
    if status:
        goldens = [g for g in goldens if g["status"].upper() == status.upper()]
    filters = {"q": q or "", "status": status or ""}
    return _templates(request).TemplateResponse(
        request,
        "golden/list.html",
        {"goldens": goldens, "filters": filters, "result_count": len(goldens), "current_path": request.url.path},
    )


@router.get("/golden-records/compare")
def golden_compare(
    request: Request,
    left: str | None = Query(default=None),
    right: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    comparison = None
    if left and right:
        comparison = present_compare_result(runtime.compare_golden_fn(left, right, runtime.repo))
    goldens = list_golden_records(runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "golden/compare.html",
        {
            "comparison": comparison,
            "goldens": goldens,
            "filters": {"left": left or "", "right": right or ""},
            "current_path": request.url.path,
        },
    )


@router.post("/golden-records/{golden_id}/override-primary/{field_name}")
def golden_override_primary(
    golden_id: str,
    field_name: str,
    request: Request,
    value_id: str = Form(...),
    actor: str = Form(...),
    reason: str = Form(...),
    runtime: WebRuntime = Depends(get_runtime),
):
    runtime.override_primary_fn(golden_id, field_name, value_id, actor, reason, runtime.repo)
    return RedirectResponse(url=f"/golden-records/{golden_id}?message=Primary+value+updated", status_code=303)


@router.get("/golden-records/merge/preview")
def golden_merge_preview(
    request: Request,
    survivor: str | None = Query(default=None),
    loser: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    preview = None
    if survivor and loser:
        preview = present_merge_preview(runtime.preview_merge_fn(survivor, loser, runtime.repo, None))
    goldens = list_golden_records(runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "golden/merge_preview.html",
        {
            "preview": preview,
            "goldens": goldens,
            "filters": {"survivor": survivor or "", "loser": loser or ""},
            "current_path": request.url.path,
        },
    )


@router.post("/golden-records/merge")
def golden_merge(
    request: Request,
    survivor_id: str = Form(...),
    loser_id: str = Form(...),
    actor: str = Form(...),
    reason: str = Form(...),
    runtime: WebRuntime = Depends(get_runtime),
):
    result = runtime.merge_golden_fn(survivor_id, loser_id, actor, reason, runtime.repo, None, None, None)
    return RedirectResponse(
        url=f"/golden-records/{result.survivor.golden_record_id}?message=Merge+completed+({result.merge_event.merge_id})",
        status_code=303,
    )


@router.get("/golden-records/merge/{merge_id}/rollback-preview")
def golden_rollback_preview(merge_id: str, request: Request, runtime: WebRuntime = Depends(get_runtime)):
    preview = present_rollback_preview(runtime.rollback_preview_fn(merge_id, runtime.repo), runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "golden/rollback_preview.html",
        {"preview": preview, "current_path": request.url.path},
    )


@router.post("/golden-records/merge/{merge_id}/rollback")
def golden_rollback(
    merge_id: str,
    request: Request,
    actor: str = Form(...),
    reason: str = Form(...),
    runtime: WebRuntime = Depends(get_runtime),
):
    result = runtime.rollback_merge_fn(merge_id, actor, reason, runtime.repo)
    return RedirectResponse(
        url=f"/golden-records/{result.survivor.golden_record_id}?message=Merge+rolled+back",
        status_code=303,
    )


@router.get("/golden-records/{golden_id}")
def golden_detail(
    golden_id: str,
    request: Request,
    message: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    golden = runtime.repo.find_golden_record(golden_id)
    if golden is None:
        raise HTTPException(status_code=404, detail="Golden record not found")
    timeline = present_timeline(runtime.timeline_fn(golden_id, runtime.repo, None))
    return _templates(request).TemplateResponse(
        request,
        "golden/detail.html",
        {
            "golden": present_golden_record(golden, runtime.repo.load_record_links()),
            "timeline": timeline,
            "message": message,
            "current_path": request.url.path,
        },
    )


@router.get("/record-links")
def record_links(
    request: Request,
    q: str | None = Query(default=None),
    status: str | None = Query(default=None),
    runtime: WebRuntime = Depends(get_runtime),
):
    links = runtime.repo.load_record_links()
    presented = [
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
    ]
    if q:
        q_lower = q.lower()
        presented = [
            l
            for l in presented
            if q_lower in l["link_id"].lower()
            or q_lower in l["source_record_id"].lower()
            or q_lower in l["golden_record_id"].lower()
        ]
    if status:
        presented = [l for l in presented if l["status"].upper() == status.upper()]
    return _templates(request).TemplateResponse(
        request,
        "record_links.html",
        {
            "links": presented,
            "filters": {"q": q or "", "status": status or ""},
            "result_count": len(presented),
            "current_path": request.url.path,
        },
    )
