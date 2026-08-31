from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from ire.web.dependencies import WebRuntime, get_runtime

router = APIRouter(prefix="/configuration", tags=["configuration"])


def _templates(request):
    return request.app.state.templates


@router.get("/sources")
def sources(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    systems = [{"code": s.code, "name": s.name, "trust_level": s.trust_level.value, "trust_score": s.trust_score, "internal": s.internal, "active": s.active} for s in runtime.config.source_systems]
    return _templates(request).TemplateResponse(request, "configuration/sources.html", {"systems": systems, "current_path": request.url.path})


@router.get("/matching")
def matching(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    policy = runtime.config.matching_policy
    return _templates(request).TemplateResponse(request, "configuration/matching.html", {"policy": policy, "current_path": request.url.path})


@router.get("/survivorship")
def survivorship(request: Request, runtime: WebRuntime = Depends(get_runtime)):
    policy = runtime.config.survivorship_policy
    return _templates(request).TemplateResponse(request, "configuration/survivorship.html", {"policy": policy, "current_path": request.url.path})
