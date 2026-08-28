from __future__ import annotations

import json
from pathlib import Path

import uvicorn

from ire.cli import main


def test_validate_config_command_returns_config_error_on_missing_files(tmp_path: Path) -> None:
    rc = main(["validate-config", "--config-dir", str(tmp_path)])
    assert rc == 2


def test_nested_storage_and_process_commands_work(tmp_path: Path) -> None:
    output_path = tmp_path / "out.json"
    rc = main(["storage", "init", "--root", str(tmp_path / "repo")])
    assert rc == 0

    record = {
        "source_system": "SIS",
        "source_pk": "1",
        "data": {
            "emplid": "E1",
            "first_name": "Alice",
            "last_name": "Chan",
            "email": "alice@example.com",
            "phone": "61234567",
            "date_of_birth": "1990-01-01",
            "gender": "F",
        },
    }
    rc = main([
        "process",
        "--root",
        str(tmp_path / "repo"),
        "--config-dir",
        str(Path(__file__).resolve().parents[1] / "config"),
        "--input",
        json.dumps(record),
    ])
    assert rc == 0

    batch_input = tmp_path / "records.json"
    batch_input.write_text(json.dumps([record]), encoding="utf-8")
    rc = main([
        "batch",
        "--root",
        str(tmp_path / "repo2"),
        "--config-dir",
        str(Path(__file__).resolve().parents[1] / "config"),
        "--input",
        str(batch_input),
        "--output",
        str(output_path),
    ])
    assert rc == 0
    assert output_path.exists()


def test_web_command_runs_uvicorn_with_localhost_defaults(monkeypatch, tmp_path: Path) -> None:
    called: dict[str, object] = {}

    def fake_run(app, host: str, port: int) -> None:
        called["app"] = app
        called["host"] = host
        called["port"] = port

    monkeypatch.setattr(uvicorn, "run", fake_run)
    rc = main([
        "web",
        "--root",
        str(tmp_path / "repo"),
        "--config-dir",
        str(Path(__file__).resolve().parents[1] / "config"),
    ])

    assert rc == 0
    assert called["host"] == "127.0.0.1"
    assert called["port"] == 8000
