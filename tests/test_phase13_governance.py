from __future__ import annotations

from pathlib import Path

from ire.config import load_config
from ire.enums import GoldenRecordStatus, LinkStatus
from ire.governance import (
    data_quality_summary,
    duplicate_scan,
    export_dataset,
    integrity_check,
    integrity_repair_preview,
    list_duplicate_candidates,
    sanitize_download_name,
    update_duplicate_candidate_status,
)
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink, SourceRecord

BASE = Path(__file__).resolve().parents[1]
CONFIG_DIR = BASE / "config"
TS = "2026-01-01T00:00:00Z"


def _value(raw: str, *, source: str, is_primary: bool = False, field: str = "email", value_id: str) -> GoldenFieldValue:
    normalized = raw.lower() if field != "phone" else "".join(ch for ch in raw if ch.isdigit())
    return GoldenFieldValue(
        raw,
        normalized,
        source,
        "SIS",
        0.9,
        is_primary,
        True,
        False,
        True,
        TS,
        value_id=value_id,
    )


def _seed_repo(root: Path) -> JsonFileRepository:
    repo = JsonFileRepository(root)
    repo.initialize_storage()
    left = GoldenRecord(
        "GR-A",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Peter", source="SRC-1", is_primary=True, field="first_name", value_id="GFV-1")],
            "last_name": [_value("Chan", source="SRC-1", is_primary=True, field="last_name", value_id="GFV-2")],
            "date_of_birth": [_value("1990-01-02", source="SRC-1", is_primary=True, field="date_of_birth", value_id="GFV-3")],
            "phone": [_value("61234567", source="SRC-1", is_primary=True, field="phone", value_id="GFV-4")],
        },
        TS,
        TS,
    )
    right = GoldenRecord(
        "GR-B",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Peter", source="SRC-2", is_primary=True, field="first_name", value_id="GFV-5")],
            "last_name": [_value("Chan", source="SRC-2", is_primary=True, field="last_name", value_id="GFV-6")],
            "date_of_birth": [_value("1990-01-02", source="SRC-2", is_primary=True, field="date_of_birth", value_id="GFV-7")],
            "phone": [_value("61234567", source="SRC-2", is_primary=True, field="phone", value_id="GFV-8")],
        },
        TS,
        TS,
    )
    repo.save_golden_records([left, right])
    source = SourceRecord(
        source_record_id="SRC-1",
        source_system="SIS",
        source_pk="PK-1",
        payload={"first_name": "Peter", "last_name": "Chan", "email": "=cmd", "phone": "123"},
        payload_hash="hash-1",
        ingested_at=TS,
    )
    repo.append_source_record(source)
    repo.save_record_links([RecordLink("L-1", "SRC-1", "GR-A", LinkStatus.ACTIVE, 0.9, TS, TS)])
    return repo


def test_duplicate_scan_and_status_lifecycle(tmp_path: Path) -> None:
    repo = _seed_repo(tmp_path / "repo")
    config = load_config(CONFIG_DIR)
    result = duplicate_scan(repo, config)
    assert result["candidate_count"] >= 1
    candidate = list_duplicate_candidates(repo)[0]
    updated = update_duplicate_candidate_status(
        repo,
        candidate.candidate_id,
        "NOT_DUPLICATE",
        actor="tester",
        reason="verified distinct",
    )
    assert updated.status.value == "NOT_DUPLICATE"


def test_integrity_and_repair_preview_side_effect_free(tmp_path: Path) -> None:
    repo = _seed_repo(tmp_path / "repo")
    before = repo.golden_records_path.read_text(encoding="utf-8")
    report = integrity_check(repo)
    preview = integrity_repair_preview(repo)
    after = repo.golden_records_path.read_text(encoding="utf-8")
    assert report.generated_at
    assert preview["preview_only"] is True
    assert before == after


def test_export_csv_masks_and_formula_safe(tmp_path: Path) -> None:
    repo = _seed_repo(tmp_path / "repo")
    config = load_config(CONFIG_DIR)
    content_type, content = export_dataset(repo, config, "activity-log", "csv")
    text = content.decode("utf-8")
    assert "text/csv" in content_type
    assert "'=cmd" in text or "=cmd" not in text
    assert sanitize_download_name("../bad name.csv") == "bad-name.csv"


def test_data_quality_summary(tmp_path: Path) -> None:
    repo = _seed_repo(tmp_path / "repo")
    config = load_config(CONFIG_DIR)
    summary = data_quality_summary(repo, config)
    assert "overall" in summary
    assert summary["overall"]["records_processed"] >= 1
