from __future__ import annotations

import json
from pathlib import Path

from ire.cli import main

BASE = Path(__file__).resolve().parents[1]
CONFIG_DIR = BASE / "config"


def test_demo_reset_requires_confirmation(tmp_path: Path) -> None:
    root = tmp_path / "demo"
    rc = main(["demo", "reset", "--root", str(root)])
    assert rc == 1


def test_demo_seed_status_and_duplicates_cli(tmp_path: Path, capsys) -> None:
    root = tmp_path / "demo"
    assert main(["storage", "init", "--root", str(root)]) == 0
    _ = capsys.readouterr()
    assert (
        main(
            [
                "demo",
                "seed",
                "--scenario",
                "standard",
                "--root",
                str(root),
                "--config-dir",
                str(CONFIG_DIR),
            ]
        )
        == 0
    )
    seeded = json.loads(capsys.readouterr().out)
    assert seeded["seeded"] is True

    assert main(["demo", "status", "--root", str(root)]) == 0
    status_payload = json.loads(capsys.readouterr().out)
    assert status_payload["manifest"]["scenario"] == "standard"

    assert main(["duplicates", "scan", "--root", str(root), "--config-dir", str(CONFIG_DIR)]) == 0
    scan_payload = json.loads(capsys.readouterr().out)
    assert "scan_run" in scan_payload

    assert main(["duplicates", "list", "--root", str(root)]) == 0
    rows = json.loads(capsys.readouterr().out)
    assert isinstance(rows, list)


def test_integrity_and_export_cli(tmp_path: Path, capsys) -> None:
    root = tmp_path / "demo"
    output = tmp_path / "export.csv"
    assert main(["storage", "init", "--root", str(root)]) == 0
    _ = capsys.readouterr()
    assert main(["integrity", "check", "--root", str(root)]) == 0
    report = json.loads(capsys.readouterr().out)
    assert "findings" in report
    assert main(
        [
            "export",
            "golden-records",
            "--format",
            "csv",
            "--output",
            str(output),
            "--root",
            str(root),
            "--config-dir",
            str(CONFIG_DIR),
        ]
    ) == 0
    assert output.exists()
