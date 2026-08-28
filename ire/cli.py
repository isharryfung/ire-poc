from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import uvicorn

from . import __version__
from .config import load_config
from .exceptions import ConfigurationError, InvalidReviewDecisionError, NotFoundError, RepositoryError
from .json_repository import JsonFileRepository
from .review import approve_review, list_review_tasks, reject_review, show_review_task
from .service import preview_record, process_batch, process_record


EXIT_SUCCESS = 0
EXIT_VALIDATION_ERROR = 1
EXIT_CONFIG_ERROR = 2
EXIT_STORAGE_ERROR = 3
EXIT_NOT_FOUND = 4
EXIT_INVALID_REVIEW = 5


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="ire", description="Identity Resolution Engine CLI")
    parser.add_argument("--version", action="version", version=f"ire {__version__}")
    subparsers = parser.add_subparsers(dest="command")

    init_storage = subparsers.add_parser("init-storage", help="Initialize JSON/JSONL storage files")
    init_storage.add_argument("--root", default="data", help="Storage root directory")

    validate_storage = subparsers.add_parser("validate-storage", help="Validate state storage files")
    validate_storage.add_argument("--root", default="data", help="Storage root directory")

    validate_config = subparsers.add_parser("validate-config", help="Validate configuration files")
    validate_config.add_argument("--config-dir", default="config", help="Configuration directory")

    config_parser = subparsers.add_parser("config", help="Configuration operations")
    config_sub = config_parser.add_subparsers(dest="config_command")
    config_validate = config_sub.add_parser("validate", help="Validate configuration files")
    config_validate.add_argument("--config-dir", default="config", help="Configuration directory")

    storage_parser = subparsers.add_parser("storage", help="Storage operations")
    storage_sub = storage_parser.add_subparsers(dest="storage_command")
    storage_init = storage_sub.add_parser("init", help="Initialize storage")
    storage_init.add_argument("--root", default="data", help="Storage root directory")
    storage_validate = storage_sub.add_parser("validate", help="Validate storage")
    storage_validate.add_argument("--root", default="data", help="Storage root directory")

    process_parser = subparsers.add_parser("process", help="Process one record")
    process_parser.add_argument("--input", required=True, help="JSON string input")
    process_parser.add_argument("--config-dir", default="config")
    process_parser.add_argument("--root", default="data")

    batch_parser = subparsers.add_parser("batch", help="Process a JSON or CSV batch file")
    batch_parser.add_argument("--input", required=True, help="Input file path")
    batch_parser.add_argument("--output", required=True, help="Output file path")
    batch_parser.add_argument("--config-dir", default="config")
    batch_parser.add_argument("--root", default="data")

    preview_parser = subparsers.add_parser("preview", help="Preview one record without persistence")
    preview_parser.add_argument("--input", required=True, help="JSON string input")
    preview_parser.add_argument("--config-dir", default="config")
    preview_parser.add_argument("--root", default="data")

    review_parser = subparsers.add_parser("review", help="Manual review operations")
    review_sub = review_parser.add_subparsers(dest="review_command")
    review_list = review_sub.add_parser("list", help="List review tasks")
    review_list.add_argument("--status", default=None)
    review_list.add_argument("--root", default="data")
    review_show = review_sub.add_parser("show", help="Show review task")
    review_show.add_argument("task_id")
    review_show.add_argument("--root", default="data")
    review_approve = review_sub.add_parser("approve", help="Approve review task")
    review_approve.add_argument("task_id")
    review_approve.add_argument("--reviewer", required=True)
    review_approve.add_argument("--golden-id", required=True)
    review_approve.add_argument("--notes")
    review_approve.add_argument("--config-dir", default="config")
    review_approve.add_argument("--root", default="data")
    review_reject = review_sub.add_parser("reject", help="Reject review task")
    review_reject.add_argument("task_id")
    review_reject.add_argument("--reviewer", required=True)
    review_reject.add_argument("--action", required=True, choices=["create-new", "invalid"])
    review_reject.add_argument("--notes")
    review_reject.add_argument("--config-dir", default="config")
    review_reject.add_argument("--root", default="data")

    golden_parser = subparsers.add_parser("golden", help="Golden record operations")
    golden_sub = golden_parser.add_subparsers(dest="golden_command")
    golden_show = golden_sub.add_parser("show", help="Show golden record")
    golden_show.add_argument("golden_id")
    golden_show.add_argument("--root", default="data")

    web_parser = subparsers.add_parser("web", help="Run the FastAPI demo server")
    web_parser.add_argument("--host", default="127.0.0.1")
    web_parser.add_argument("--port", type=int, default=8000)
    web_parser.add_argument("--root", default="data/demo-run")
    web_parser.add_argument("--config-dir", default="config")

    return parser


def _print_json(payload) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))


def _load_runtime(config_dir: str, root: str):
    config = load_config(config_dir)
    repo = JsonFileRepository(root)
    repo.initialize_storage()
    return config, repo


def _read_batch_records(path: str) -> list[dict]:
    input_path = Path(path)
    if input_path.suffix.lower() == ".csv":
        with input_path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            records = []
            for row in reader:
                source_system = row.pop("source_system")
                source_pk = row.pop("source_pk")
                data = {k: v for k, v in row.items() if v not in (None, "")}
                records.append({"source_system": source_system, "source_pk": source_pk, "data": data})
            return records
    payload = json.loads(input_path.read_text(encoding="utf-8"))
    return payload if isinstance(payload, list) else [payload]


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        if args.command == "init-storage":
            repo = JsonFileRepository(args.root)
            repo.initialize_storage()
            print(f"initialized storage under {args.root}")
            return EXIT_SUCCESS

        if args.command == "validate-storage":
            repo = JsonFileRepository(args.root)
            repo.validate_storage()
            print(f"storage is valid under {args.root}")
            return EXIT_SUCCESS

        if args.command == "validate-config":
            config = load_config(args.config_dir)
            print(
                "config is valid "
                f"(source_systems={len(config.source_systems)}, "
                f"matching_policy={config.matching_policy.version}, "
                f"survivorship_policy={config.survivorship_policy.version})"
            )
            return EXIT_SUCCESS

        if args.command == "config" and args.config_command == "validate":
            return main(["validate-config", "--config-dir", args.config_dir])

        if args.command == "storage" and args.storage_command == "init":
            return main(["init-storage", "--root", args.root])

        if args.command == "storage" and args.storage_command == "validate":
            return main(["validate-storage", "--root", args.root])

        if args.command == "process":
            config, repo = _load_runtime(args.config_dir, args.root)
            result = process_record(json.loads(args.input), config, repo)
            _print_json(result.__dict__)
            return EXIT_VALIDATION_ERROR if result.outcome == "VALIDATION_FAILED" else EXIT_SUCCESS

        if args.command == "batch":
            config, repo = _load_runtime(args.config_dir, args.root)
            results = process_batch(_read_batch_records(args.input), config, repo)
            payload = [result.__dict__ for result in results]
            Path(args.output).write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            _print_json({"count": len(payload), "output": args.output})
            return EXIT_SUCCESS

        if args.command == "preview":
            config, repo = _load_runtime(args.config_dir, args.root)
            result = preview_record(json.loads(args.input), config, repo)
            _print_json(result)
            return EXIT_VALIDATION_ERROR if result["outcome"] == "VALIDATION_FAILED" else EXIT_SUCCESS

        if args.command == "review" and args.review_command == "list":
            repo = JsonFileRepository(args.root)
            repo.initialize_storage()
            _print_json([task.to_dict() for task in list_review_tasks(args.status, repo)])
            return EXIT_SUCCESS

        if args.command == "review" and args.review_command == "show":
            repo = JsonFileRepository(args.root)
            repo.initialize_storage()
            detail = show_review_task(args.task_id, repo)
            _print_json({
                "task": detail.task.to_dict(),
                "source_record": detail.source_record.to_dict(),
                "normalized": detail.normalized,
                "candidates": detail.candidates,
                "safety_flags": detail.safety_flags,
                "suggested_decision": detail.suggested_decision,
            })
            return EXIT_SUCCESS

        if args.command == "review" and args.review_command == "approve":
            config, repo = _load_runtime(args.config_dir, args.root)
            golden = approve_review(args.task_id, args.golden_id, args.reviewer, args.notes, repo, config)
            _print_json(golden.to_dict())
            return EXIT_SUCCESS

        if args.command == "review" and args.review_command == "reject":
            config, repo = _load_runtime(args.config_dir, args.root)
            _print_json(reject_review(args.task_id, args.action, args.reviewer, args.notes, repo, config))
            return EXIT_SUCCESS

        if args.command == "golden" and args.golden_command == "show":
            repo = JsonFileRepository(args.root)
            repo.initialize_storage()
            golden = repo.find_golden_record(args.golden_id)
            if golden is None:
                raise NotFoundError(f"golden record not found: {args.golden_id}")
            _print_json(golden.to_dict())
            return EXIT_SUCCESS

        if args.command == "web":
            from .web import create_app

            app = create_app(root_dir=args.root, config_dir=args.config_dir)
            uvicorn.run(app, host=args.host, port=args.port)
            return EXIT_SUCCESS
    except ConfigurationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_CONFIG_ERROR
    except NotFoundError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_NOT_FOUND
    except InvalidReviewDecisionError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_INVALID_REVIEW
    except RepositoryError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_STORAGE_ERROR
    except (ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_VALIDATION_ERROR

    parser.print_help(sys.stderr)
    return EXIT_CONFIG_ERROR
