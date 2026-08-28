from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.templating import Jinja2Templates

from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import list_golden_records, present_golden_record

router = APIRouter(tags=["golden"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


@router.get("/golden-records")
def golden_list(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    return _templates(request).TemplateResponse(request, "golden/list.html", {"goldens": list_golden_records(runtime.repo)})


@router.get("/golden-records/{golden_id}")
def golden_detail(golden_id: str, request: Request, runtime: WebRuntime = Depends(get_runtime)):
    golden = runtime.repo.find_golden_record(golden_id)
    if golden is None:
        raise HTTPException(status_code=404, detail="Golden record not found")
    return _templates(request).TemplateResponse(
        request,
        "golden/detail.html",
        {"golden": present_golden_record(golden, runtime.repo.load_record_links())},
    )
