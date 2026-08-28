from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from ire import __version__
from ire.exceptions import ConfigurationError, InvalidReviewDecisionError, RepositoryError, ValidationError
from ire.web.dependencies import STATIC_DIR, TEMPLATES_DIR, WebRuntime, create_runtime
from ire.web.routers import api, dashboard, golden, records, reviews


def create_app(
    root_dir: str | Path = "data",
    config_dir: str | Path = "config",
    *,
    runtime: WebRuntime | None = None,
) -> FastAPI:
    app = FastAPI(
        title="IRE Phase 1.1 Demo",
        version=__version__,
        docs_url="/docs",
        redoc_url=None,
    )
    app.state.runtime = runtime or create_runtime(root_dir=root_dir, config_dir=config_dir)
    app.state.templates = Jinja2Templates(directory=str(TEMPLATES_DIR))
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

    @app.exception_handler(RequestValidationError)
    async def request_validation_handler(request: Request, exc: RequestValidationError):
        return _error_response(request, 422, "VALIDATION_ERROR", "The request payload is invalid.", details=exc.errors())

    @app.exception_handler(ValidationError)
    async def validation_handler(request: Request, exc: ValidationError):
        return _error_response(request, 400, "VALIDATION_ERROR", str(exc))

    @app.exception_handler(InvalidReviewDecisionError)
    async def review_conflict_handler(request: Request, exc: InvalidReviewDecisionError):
        return _error_response(request, 409, "REVIEW_CONFLICT", "This review task is already completed or the requested decision is invalid.")

    @app.exception_handler(ConfigurationError)
    async def configuration_handler(request: Request, exc: ConfigurationError):
        return _error_response(request, 500, "CONFIGURATION_ERROR", "The demo configuration is invalid or unavailable.")

    @app.exception_handler(RepositoryError)
    async def repository_handler(request: Request, exc: RepositoryError):
        return _error_response(request, 500, "STORAGE_ERROR", "The demo storage is unavailable or unreadable.")

    @app.exception_handler(HTTPException)
    async def http_handler(request: Request, exc: HTTPException):
        code = {404: "NOT_FOUND", 409: "CONFLICT"}.get(exc.status_code, "HTTP_ERROR")
        return _error_response(request, exc.status_code, code, str(exc.detail))

    @app.exception_handler(Exception)
    async def unexpected_handler(request: Request, exc: Exception):
        return _error_response(request, 500, "UNEXPECTED_ERROR", "An unexpected demo error occurred.")

    app.include_router(api.router)
    app.include_router(dashboard.router)
    app.include_router(records.router)
    app.include_router(golden.router)
    app.include_router(reviews.router)
    return app


def _error_response(request: Request, status_code: int, code: str, message: str, details=None):
    accepts = {part.split(";")[0].strip() for part in request.headers.get("accept", "").split(",") if part.strip()}
    if request.url.path.startswith("/api/") or "application/json" in accepts:
        payload = {"error": {"status": status_code, "code": code, "message": message}}
        if details is not None:
            payload["error"]["details"] = details
        return JSONResponse(status_code=status_code, content=payload)
    template_name = {404: "errors/404.html", 409: "errors/409.html", 500: "errors/500.html"}.get(status_code, "errors/error.html")
    templates: Jinja2Templates = request.app.state.templates
    return templates.TemplateResponse(request, template_name, {"status_code": status_code, "code": code, "message": message}, status_code=status_code)
