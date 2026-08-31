from pathlib import Path

import pytest

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.exceptions import NotFoundError, ValidationError
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink
from ire.primary_override import override_primary_value

TS = "2026-01-01T00:00:00Z"


def _value(
    raw: str,
    *,
    source: str,
    system: str = "SIS",
    trust: float = 0.9,
    is_primary: bool = False,
    manual_lock: bool = False,
    value_id: str | None = None,
) -> GoldenFieldValue:
    kwargs = {}
    if value_id is not None:
        kwargs["value_id"] = value_id
    return GoldenFieldValue(
        raw,
        raw.lower(),
        source,
        system,
        trust,
        is_primary,
        True,
        manual_lock,
        True,
        TS,
        **kwargs,
    )


def _repo(tmp_path: Path, record: GoldenRecord) -> JsonFileRepository:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()
    repo.save_golden_records([record])
    return repo


def test_override_primary_sets_lock_and_bumps_version(tmp_path: Path) -> None:
    record = GoldenRecord(
        "GR-1",
        GoldenRecordStatus.ACTIVE,
        {
            "email": [
                _value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a"),
                _value("b@x.com", source="SRC-2", value_id="GFV-b"),
            ]
        },
        TS,
        TS,
    )
    repo = _repo(tmp_path, record)

    result = override_primary_value("GR-1", "email", "GFV-b", "harry", "correction", repo)

    assert result.new_primary_value_id == "GFV-b"
    assert result.previous_primary_value_id == "GFV-a"
    saved = repo.find_golden_record("GR-1")
    assert saved is not None
    assert saved.version == 2
    primaries = {v.value_id: (v.is_primary, v.manual_lock) for v in saved.fields["email"]}
    assert primaries["GFV-b"] == (True, True)
    assert primaries["GFV-a"] == (False, False)


def test_override_records_event_and_audit(tmp_path: Path) -> None:
    record = GoldenRecord(
        "GR-1",
        GoldenRecordStatus.ACTIVE,
        {"email": [_value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a"),
                   _value("b@x.com", source="SRC-2", value_id="GFV-b")]},
        TS,
        TS,
    )
    repo = _repo(tmp_path, record)

    override_primary_value("GR-1", "email", "GFV-b", "harry", "correction", repo)

    events = repo.load_primary_override_events()
    assert len(events) == 1
    assert events[0].field_name == "email"
    assert events[0].value_id == "GFV-b"
    audits = [a for a in repo.load_audit_events() if a.entity_id == "GR-1"]
    assert any(a.event_type == "PRIMARY_OVERRIDE" for a in audits)


def test_override_unknown_record_raises(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()
    with pytest.raises(NotFoundError):
        override_primary_value("GR-x", "email", "GFV-b", "harry", "r", repo)


def test_override_unknown_value_raises(tmp_path: Path) -> None:
    record = GoldenRecord(
        "GR-1",
        GoldenRecordStatus.ACTIVE,
        {"email": [_value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a")]},
        TS,
        TS,
    )
    repo = _repo(tmp_path, record)
    with pytest.raises(NotFoundError):
        override_primary_value("GR-1", "email", "GFV-missing", "harry", "r", repo)


def test_override_superseded_record_raises(tmp_path: Path) -> None:
    record = GoldenRecord(
        "GR-1",
        GoldenRecordStatus.SUPERSEDED,
        {"email": [_value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a")]},
        TS,
        TS,
        superseded_by="GR-2",
    )
    repo = _repo(tmp_path, record)
    with pytest.raises(ValidationError):
        override_primary_value("GR-1", "email", "GFV-a", "harry", "r", repo)
