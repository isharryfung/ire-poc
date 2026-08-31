from pathlib import Path

import pytest

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.exceptions import NotFoundError
from ire.golden_merge import merge_golden_records, rollback_merge
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink
from ire.primary_override import override_primary_value
from ire.timeline import get_golden_timeline

TS = "2026-01-01T00:00:00Z"


def _value(raw: str, *, source: str, is_primary: bool = False, value_id: str) -> GoldenFieldValue:
    return GoldenFieldValue(
        raw, raw.lower(), source, "SIS", 0.9, is_primary, True, False, True, TS, value_id=value_id
    )


def _repo(tmp_path: Path) -> JsonFileRepository:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()
    survivor = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"email": [_value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a"),
                   _value("c@x.com", source="SRC-3", value_id="GFV-c")]},
        TS, TS,
    )
    loser = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"phone": [_value("61234567", source="SRC-2", is_primary=True, value_id="GFV-p")]},
        TS, TS,
    )
    repo.save_golden_records([survivor, loser])
    repo.save_record_links([RecordLink("LNK-1", "SRC-1", "GR-A", LinkStatus.ACTIVE, 0.9, TS, TS)])
    return repo


def test_timeline_includes_lifecycle_and_link(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    entries = get_golden_timeline("GR-A", repo)
    categories = {e.category for e in entries}
    assert "LIFECYCLE" in categories
    assert "LINK" in categories


def test_timeline_reflects_override_and_merge(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    override_primary_value("GR-A", "email", "GFV-c", "harry", "fix", repo)
    merge_golden_records("GR-A", "GR-B", "harry", "same", repo)

    entries = get_golden_timeline("GR-A", repo)
    types = {e.event_type for e in entries}
    assert "PRIMARY_OVERRIDE" in types
    assert "GOLDEN_MERGE" in types
    # newest first
    timestamps = [e.timestamp for e in entries]
    assert timestamps == sorted(timestamps, reverse=True)


def test_timeline_category_filter(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    override_primary_value("GR-A", "email", "GFV-c", "harry", "fix", repo)
    entries = get_golden_timeline("GR-A", repo, category_filter="OVERRIDE")
    assert entries
    assert all(e.category == "OVERRIDE" for e in entries)


def test_timeline_does_not_leak_raw_values(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    override_primary_value("GR-A", "email", "GFV-c", "harry", "fix", repo)
    entries = get_golden_timeline("GR-A", repo)
    joined = " ".join(e.summary for e in entries)
    assert "c@x.com" not in joined
    assert "a@x.com" not in joined


def test_timeline_rollback_entry(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    result = merge_golden_records("GR-A", "GR-B", "harry", "same", repo)
    rollback_merge(result.merge_event.merge_id, "harry", "oops", repo)
    entries = get_golden_timeline("GR-A", repo)
    assert any(e.event_type == "MERGE_ROLLBACK" for e in entries)


def test_timeline_unknown_record_raises(tmp_path: Path) -> None:
    repo = _repo(tmp_path)
    with pytest.raises(NotFoundError):
        get_golden_timeline("GR-x", repo)
