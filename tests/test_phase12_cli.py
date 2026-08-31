from __future__ import annotations

import json
from pathlib import Path

from ire.cli import main
from ire.enums import GoldenRecordStatus, LinkStatus
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink

TS = "2026-01-01T00:00:00Z"


def _value(raw: str, *, source: str, is_primary: bool = False, value_id: str) -> GoldenFieldValue:
    return GoldenFieldValue(
        raw, raw.lower(), source, "SIS", 0.9, is_primary, True, False, True, TS, value_id=value_id
    )


def _seed(root: Path) -> None:
    repo = JsonFileRepository(root)
    repo.initialize_storage()
    survivor = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {
            "email": [
                _value("a@example.com", source="SRC-1", is_primary=True, value_id="GFV-a1"),
                _value("alt@example.com", source="SRC-3", value_id="GFV-a2"),
            ],
        },
        TS, TS,
    )
    loser = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"phone": [_value("61234567", source="SRC-2", is_primary=True, value_id="GFV-b1")]},
        TS, TS,
    )
    repo.save_golden_records([survivor, loser])
    repo.save_record_links([RecordLink("LNK-1", "SRC-2", "GR-B", LinkStatus.ACTIVE, 0.9, TS, TS)])


def test_cli_compare(tmp_path: Path, capsys) -> None:
    root = tmp_path / "repo"
    _seed(root)
    rc = main(["golden", "compare", "GR-A", "GR-B", "--root", str(root)])
    assert rc == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["can_merge"] is True


def test_cli_merge_preview(tmp_path: Path, capsys) -> None:
    root = tmp_path / "repo"
    _seed(root)
    rc = main(["golden", "merge-preview", "--survivor", "GR-A", "--loser", "GR-B", "--root", str(root)])
    assert rc == 0
    payload = json.loads(capsys.readouterr().out)
    assert "phone" in payload["merged_fields"]
    assert payload["moved_link_ids"] == ["LNK-1"]


def test_cli_override_primary(tmp_path: Path, capsys) -> None:
    root = tmp_path / "repo"
    _seed(root)
    rc = main([
        "golden", "override-primary", "GR-A",
        "--field", "email", "--value-id", "GFV-a2",
        "--actor", "harry", "--reason", "fix", "--root", str(root),
    ])
    assert rc == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["new_primary_value_id"] == "GFV-a2"


def test_cli_merge_rollback_and_timeline(tmp_path: Path, capsys) -> None:
    root = tmp_path / "repo"
    _seed(root)
    rc = main([
        "golden", "merge", "--survivor", "GR-A", "--loser", "GR-B",
        "--actor", "harry", "--reason", "same", "--root", str(root),
    ])
    assert rc == 0
    merge_payload = json.loads(capsys.readouterr().out)
    merge_id = merge_payload["merge_id"]
    assert merge_payload["loser"]["status"] == "SUPERSEDED"

    rc = main(["golden", "rollback-preview", merge_id, "--root", str(root)])
    assert rc == 0
    assert json.loads(capsys.readouterr().out)["can_rollback"] is True

    rc = main([
        "golden", "rollback", merge_id,
        "--actor", "harry", "--reason", "oops", "--root", str(root),
    ])
    assert rc == 0
    rollback_payload = json.loads(capsys.readouterr().out)
    assert rollback_payload["loser"]["status"] == "ACTIVE"

    rc = main(["golden", "timeline", "GR-A", "--root", str(root)])
    assert rc == 0
    entries = json.loads(capsys.readouterr().out)
    types = {e["event_type"] for e in entries}
    assert "GOLDEN_MERGE" in types
    assert "MERGE_ROLLBACK" in types


def test_cli_merge_blocked_exit_code(tmp_path: Path, capsys) -> None:
    root = tmp_path / "repo"
    repo = JsonFileRepository(root)
    repo.initialize_storage()
    left = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("AB1234567", source="SRC-1", is_primary=True, value_id="GFV-h1")]}, TS, TS,
    )
    right = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("CD7654321", source="SRC-2", is_primary=True, value_id="GFV-h2")]}, TS, TS,
    )
    repo.save_golden_records([left, right])
    rc = main([
        "golden", "merge", "--survivor", "GR-A", "--loser", "GR-B",
        "--actor", "harry", "--reason", "x", "--root", str(root),
    ])
    assert rc == 1


def test_cli_compare_missing_exit_code(tmp_path: Path) -> None:
    root = tmp_path / "repo"
    _seed(root)
    rc = main(["golden", "compare", "GR-A", "GR-X", "--root", str(root)])
    assert rc == 4
