from __future__ import annotations

import json
from pathlib import Path

from ire.config import load_config
from ire.ingestion import ingest_csv_file, ingest_json_file, ingest_record
from ire.json_repository import JsonFileRepository


BASE = Path(__file__).resolve().parents[1]


def _record(source_pk: str, email: str = "alice@example.com") -> dict:
    return {
        "source_system": "SIS",
        "source_pk": source_pk,
        "data": {
            "emplid": "E1",
            "first_name": "Alice",
            "last_name": "Chan",
            "email": email,
            "phone": "61234567",
            "date_of_birth": "1990-01-01",
            "gender": "F",
        },
    }


def test_ingest_detects_duplicate_and_revision(tmp_path: Path) -> None:
    config = load_config(BASE / "config")
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    first = ingest_record(_record("1"), config, repo)
    duplicate = ingest_record(_record("1"), config, repo)
    revised = ingest_record(_record("1", email="alice2@example.com"), config, repo)

    assert first.source_record is not None
    assert duplicate.is_duplicate is True
    assert revised.is_revision is True
    assert revised.previous_source_record_id == first.source_record.source_record_id


def test_ingest_json_and_csv_files(tmp_path: Path) -> None:
    config = load_config(BASE / "config")
    repo = JsonFileRepository(tmp_path / "repo")
    repo.initialize_storage()

    json_path = tmp_path / "records.json"
    json_path.write_text(json.dumps([_record("1"), _record("2")]), encoding="utf-8")
    csv_path = tmp_path / "records.csv"
    csv_path.write_text(
        "source_system,source_pk,first_name,last_name,email,phone,date_of_birth,gender\n"
        "SIS,3,Alice,Chan,alice@example.com,61234567,1990-01-01,F\n",
        encoding="utf-8",
    )

    assert len(ingest_json_file(str(json_path), config, repo)) == 2
    assert len(ingest_csv_file(str(csv_path), config, repo)) == 1
