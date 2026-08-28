from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from fastapi.templating import Jinja2Templates

from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import dashboard_snapshot

router = APIRouter(tags=["dashboard"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


@router.get("/")
def dashboard(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    return _templates(request).TemplateResponse(
        request,
        "dashboard.html",
        {"snapshot": dashboard_snapshot(runtime.repo)},
    )
