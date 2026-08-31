from pathlib import Path

import pytest

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.exceptions import MergeBlockedError, NotFoundError, StaleVersionError, ValidationError
from ire.golden_merge import (
    compare_golden_records,
    merge_golden_records,
    preview_golden_merge,
    rollback_merge,
    rollback_merge_preview,
)
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink

TS = "2026-01-01T00:00:00Z"


def _value(
    raw: str,
    *,
    source: str,
    system: str = "SIS",
    trust: float = 0.9,
    is_primary: bool = False,
    manual_lock: bool = False,
    value_id: str,
) -> GoldenFieldValue:
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
        value_id=value_id,
    )


def _link(link_id: str, source: str, golden: str, status: LinkStatus = LinkStatus.ACTIVE) -> RecordLink:
    return RecordLink(link_id, source, golden, status, 0.9, TS, TS)


def _repo(tmp_path: Path, records: list[GoldenRecord], links: list[RecordLink] | None = None) -> JsonFileRepository:
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()
    repo.save_golden_records(records)
    if links:
        repo.save_record_links(links)
    return repo


def _survivor() -> GoldenRecord:
    return GoldenRecord(
        "GR-A",
        GoldenRecordStatus.ACTIVE,
        {
            "email": [_value("a@x.com", source="SRC-1", is_primary=True, value_id="GFV-a1")],
            "first_name": [_value("Alice", source="SRC-1", is_primary=True, value_id="GFV-a2")],
        },
        TS,
        TS,
    )


def _loser() -> GoldenRecord:
    return GoldenRecord(
        "GR-B",
        GoldenRecordStatus.ACTIVE,
        {
            "email": [_value("b@x.com", source="SRC-2", trust=0.5, is_primary=True, value_id="GFV-b1")],
            "phone": [_value("61234567", source="SRC-2", is_primary=True, value_id="GFV-b2")],
        },
        TS,
        TS,
    )


def test_compare_reports_agree_and_different(tmp_path: Path) -> None:
    left = _survivor()
    right = _loser()
    repo = _repo(tmp_path, [left, right])
    result = compare_golden_records("GR-A", "GR-B", repo)
    statuses = {c.field_name: c.status for c in result.fields}
    assert statuses["email"] == "DIFFERENT"
    assert statuses["first_name"] == "LEFT_ONLY"
    assert statuses["phone"] == "RIGHT_ONLY"
    assert result.can_merge is True


def test_compare_flags_strong_identifier_conflict(tmp_path: Path) -> None:
    left = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("AB1234567", source="SRC-1", is_primary=True, value_id="GFV-h1")]},
        TS, TS,
    )
    right = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("CD7654321", source="SRC-2", is_primary=True, value_id="GFV-h2")]},
        TS, TS,
    )
    repo = _repo(tmp_path, [left, right])
    result = compare_golden_records("GR-A", "GR-B", repo)
    assert result.can_merge is False
    assert "hkid" in result.strong_identifier_conflicts


def test_preview_is_side_effect_free(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()], [_link("LNK-1", "SRC-2", "GR-B")])
    before = repo.find_golden_record("GR-A")
    preview = preview_golden_merge("GR-A", "GR-B", repo)
    assert preview.can_merge is True
    assert "phone" in preview.merged_fields
    assert preview.moved_link_ids == ["LNK-1"]
    # No state change
    assert repo.find_golden_record("GR-A") == before
    assert repo.find_golden_record("GR-B").status == GoldenRecordStatus.ACTIVE


def test_merge_supersedes_loser_and_moves_links(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()], [_link("LNK-1", "SRC-2", "GR-B")])
    result = merge_golden_records("GR-A", "GR-B", "harry", "same person", repo)

    survivor = repo.find_golden_record("GR-A")
    loser = repo.find_golden_record("GR-B")
    assert loser.status == GoldenRecordStatus.SUPERSEDED
    assert loser.superseded_by == "GR-A"
    assert "GR-B" in survivor.merged_from
    assert survivor.version == 2
    assert "phone" in survivor.fields
    links = {l.link_id: l for l in repo.load_record_links()}
    assert links["LNK-1"].golden_record_id == "GR-A"
    assert result.moved_link_ids == ["LNK-1"]


def test_merge_blocks_on_strong_identifier_conflict(tmp_path: Path) -> None:
    left = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"emplid": [_value("111", source="SRC-1", is_primary=True, value_id="GFV-e1")]},
        TS, TS,
    )
    right = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"emplid": [_value("222", source="SRC-2", is_primary=True, value_id="GFV-e2")]},
        TS, TS,
    )
    repo = _repo(tmp_path, [left, right])
    with pytest.raises(MergeBlockedError):
        merge_golden_records("GR-A", "GR-B", "harry", "r", repo)


def test_merge_blocks_on_dob_conflict(tmp_path: Path) -> None:
    left = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"date_of_birth": [_value("1990-01-01", source="SRC-1", is_primary=True, value_id="GFV-d1")]},
        TS, TS,
    )
    right = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"date_of_birth": [_value("1991-02-02", source="SRC-2", is_primary=True, value_id="GFV-d2")]},
        TS, TS,
    )
    repo = _repo(tmp_path, [left, right])
    with pytest.raises(MergeBlockedError):
        merge_golden_records("GR-A", "GR-B", "harry", "r", repo)


def test_merge_optimistic_concurrency(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()])
    with pytest.raises(StaleVersionError):
        merge_golden_records("GR-A", "GR-B", "harry", "r", repo, expected_survivor_version=99)


def test_merge_preserves_manual_lock(tmp_path: Path) -> None:
    survivor = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {"email": [_value("a@x.com", source="SRC-1", trust=0.1, is_primary=True, manual_lock=True, value_id="GFV-a1")]},
        TS, TS,
    )
    loser = GoldenRecord(
        "GR-B", GoldenRecordStatus.ACTIVE,
        {"email": [_value("b@x.com", source="SRC-2", trust=0.99, is_primary=True, value_id="GFV-b1")]},
        TS, TS,
    )
    repo = _repo(tmp_path, [survivor, loser])
    merge_golden_records("GR-A", "GR-B", "harry", "r", repo)
    email_values = {v.value_id: v.is_primary for v in repo.find_golden_record("GR-A").fields["email"]}
    assert email_values["GFV-a1"] is True
    assert email_values["GFV-b1"] is False


def test_rollback_restores_prior_state(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()], [_link("LNK-1", "SRC-2", "GR-B")])
    result = merge_golden_records("GR-A", "GR-B", "harry", "same", repo)
    merge_id = result.merge_event.merge_id

    preview = rollback_merge_preview(merge_id, repo)
    assert preview.can_rollback is True

    rollback_merge(merge_id, "harry", "mistake", repo)
    survivor = repo.find_golden_record("GR-A")
    loser = repo.find_golden_record("GR-B")
    assert loser.status == GoldenRecordStatus.ACTIVE
    assert loser.superseded_by is None
    assert "phone" not in survivor.fields
    links = {l.link_id: l for l in repo.load_record_links()}
    assert links["LNK-1"].golden_record_id == "GR-B"


def test_double_rollback_blocked(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()])
    result = merge_golden_records("GR-A", "GR-B", "harry", "same", repo)
    merge_id = result.merge_event.merge_id
    rollback_merge(merge_id, "harry", "mistake", repo)
    assert rollback_merge_preview(merge_id, repo).can_rollback is False
    with pytest.raises(ValidationError):
        rollback_merge(merge_id, "harry", "again", repo)


def test_merge_records_history_and_audit(tmp_path: Path) -> None:
    repo = _repo(tmp_path, [_survivor(), _loser()])
    merge_golden_records("GR-A", "GR-B", "harry", "same", repo)
    history = repo.load_merge_history_events()
    assert any(h.winner_golden_record_id == "GR-A" for h in history)
    assert any(a.event_type == "GOLDEN_MERGE" for a in repo.load_audit_events())
