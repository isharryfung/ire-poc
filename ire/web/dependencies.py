from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from fastapi import Request

from ire.config import IREConfig, load_config
from ire.json_repository import JsonFileRepository
from ire.repository import IRERepository
from ire.review import approve_review, reject_review, show_review_task
from ire.service import ProcessResult, preview_record, process_batch, process_record

WEB_DIR = Path(__file__).resolve().parent
TEMPLATES_DIR = WEB_DIR / "templates"
STATIC_DIR = WEB_DIR / "static"


@dataclass
class WebRuntime:
    root_dir: Path
    config_dir: Path
    config: IREConfig
    repo: IRERepository
    process_record_fn: Callable[[dict, IREConfig, IRERepository], ProcessResult] = process_record
    preview_record_fn: Callable[[dict, IREConfig, IRERepository], dict] = preview_record
    process_batch_fn: Callable[[list[dict], IREConfig, IRERepository], list[ProcessResult]] = process_batch
    show_review_task_fn: Callable[[str, IRERepository], object] = show_review_task
    approve_review_fn: Callable[[str, str, str, str | None, IRERepository, IREConfig], object] = approve_review
    reject_review_fn: Callable[[str, str, str, str | None, IRERepository, IREConfig], dict] = reject_review


def create_runtime(
    root_dir: str | Path = "data",
    config_dir: str | Path = "config",
    *,
    config: IREConfig | None = None,
    repo: IRERepository | None = None,
    process_record_fn: Callable[[dict, IREConfig, IRERepository], ProcessResult] = process_record,
    preview_record_fn: Callable[[dict, IREConfig, IRERepository], dict] = preview_record,
    process_batch_fn: Callable[[list[dict], IREConfig, IRERepository], list[ProcessResult]] = process_batch,
    show_review_task_fn: Callable[[str, IRERepository], object] = show_review_task,
    approve_review_fn: Callable[[str, str, str, str | None, IRERepository, IREConfig], object] = approve_review,
    reject_review_fn: Callable[[str, str, str, str | None, IRERepository, IREConfig], dict] = reject_review,
) -> WebRuntime:
    resolved_root = Path(root_dir).resolve()
    resolved_config = Path(config_dir).resolve()
    runtime_config = config or load_config(resolved_config)
    runtime_repo = repo or JsonFileRepository(resolved_root)
    runtime_repo.initialize_storage()
    return WebRuntime(
        root_dir=resolved_root,
        config_dir=resolved_config,
        config=runtime_config,
        repo=runtime_repo,
        process_record_fn=process_record_fn,
        preview_record_fn=preview_record_fn,
        process_batch_fn=process_batch_fn,
        show_review_task_fn=show_review_task_fn,
        approve_review_fn=approve_review_fn,
        reject_review_fn=reject_review_fn,
    )


def get_runtime(request: Request) -> WebRuntime:
    return request.app.state.runtime
