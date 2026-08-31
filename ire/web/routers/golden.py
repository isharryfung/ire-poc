from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.templating import Jinja2Templates

from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import list_golden_records, present_golden_record

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


@router.get("/golden-records/{golden_id}")
def golden_detail(golden_id: str, request: Request, runtime: WebRuntime = Depends(get_runtime)):
    golden = runtime.repo.find_golden_record(golden_id)
    if golden is None:
        raise HTTPException(status_code=404, detail="Golden record not found")
    return _templates(request).TemplateResponse(
        request,
        "golden/detail.html",
        {"golden": present_golden_record(golden, runtime.repo.load_record_links()), "current_path": request.url.path},
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
