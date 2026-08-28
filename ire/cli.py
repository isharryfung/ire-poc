from __future__ import annotations

import argparse
import sys

from . import __version__
from .config import load_config
from .exceptions import ConfigurationError, RepositoryError
from .json_repository import JsonFileRepository


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

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        if args.command == "init-storage":
            repo = JsonFileRepository(args.root)
            repo.initialize_storage()
            print(f"initialized storage under {args.root}")
            return 0

        if args.command == "validate-storage":
            repo = JsonFileRepository(args.root)
            repo.validate_storage()
            print(f"storage is valid under {args.root}")
            return 0

        if args.command == "validate-config":
            config = load_config(args.config_dir)
            print(
                "config is valid "
                f"(source_systems={len(config.source_systems)}, "
                f"matching_policy={config.matching_policy.version}, "
                f"survivorship_policy={config.survivorship_policy.version})"
            )
            return 0
    except (ConfigurationError, RepositoryError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    parser.print_help(sys.stderr)
    return 1
