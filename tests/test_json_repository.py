import json
from pathlib import Path

import pytest

from ire.enums import GoldenRecordStatus
from ire.exceptions import DuplicateSourceRecordError, RepositoryError
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, SourceRecord

TS = "2026-01-01T00:00:00Z"


def test_repository_initialization_creates_files(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    assert (tmp_path / "state" / "golden_records.json").exists()
    assert (tmp_path / "state" / "record_links.json").exists()
    assert (tmp_path / "state" / "review_tasks.json").exists()
    assert (tmp_path / "events" / "source_records.jsonl").exists()


def test_jsonl_append_source_records(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    record = SourceRecord("SRC-1", "SIS", "123", {"name": "Alice"}, "hash-1", TS)
    repo.append_source_record(record)

    lines = (tmp_path / "events" / "source_records.jsonl").read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 1
    assert json.loads(lines[0])["source_record_id"] == "SRC-1"


def test_atomic_state_write_and_backup(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    field = GoldenFieldValue("Alice", "alice", "SRC-1", "SIS", 0.9, True, True, False, True, TS)
    first = GoldenRecord("GR-1", GoldenRecordStatus.ACTIVE, {"name": [field]}, TS, TS)
    second = GoldenRecord("GR-2", GoldenRecordStatus.ACTIVE, {"name": [field]}, TS, TS)

    repo.save_golden_records([first])
    repo.save_golden_records([second])

    backup_path = tmp_path / "state" / "golden_records.json.bak"
    assert backup_path.exists()
    backup = json.loads(backup_path.read_text(encoding="utf-8"))
    assert backup[0]["golden_record_id"] == "GR-1"


def test_duplicate_external_payload_detection_and_lookup(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    first = SourceRecord("SRC-1", "SIS", "123", {"name": "Alice"}, "h1", TS)
    revised = SourceRecord("SRC-2", "SIS", "123", {"name": "Alice B"}, "h2", TS, supersedes_source_record_id="SRC-1")
    duplicate = SourceRecord("SRC-3", "SIS", "123", {"name": "Alice"}, "h1", TS)

    repo.append_source_record(first)
    repo.append_source_record(revised)

    hits = repo.find_source_records_by_external_key("SIS", "123")
    assert len(hits) == 2
    assert repo.find_source_record_by_payload_hash("SIS", "123", "h1") is not None

    with pytest.raises(DuplicateSourceRecordError):
        repo.append_source_record(duplicate)


def test_corrupted_json_is_detected_and_not_overwritten(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    broken_path = tmp_path / "state" / "golden_records.json"
    broken_path.write_text("{not-json", encoding="utf-8")

    field = GoldenFieldValue("Alice", "alice", "SRC-1", "SIS", 0.9, True, True, False, True, TS)
    record = GoldenRecord("GR-1", GoldenRecordStatus.ACTIVE, {"name": [field]}, TS, TS)

    with pytest.raises(RepositoryError):
        repo.save_golden_records([record])

    assert broken_path.read_text(encoding="utf-8") == "{not-json"
