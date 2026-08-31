from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink
from ire.web.app import create_app

BASE = Path(__file__).resolve().parents[2]
CONFIG_DIR = BASE / "config"
TS = "2026-01-01T00:00:00Z"


def _value(raw: str, *, source: str, system: str, value_id: str, trust: float = 0.9, is_primary: bool = False) -> GoldenFieldValue:
    return GoldenFieldValue(raw, raw.lower(), source, system, trust, is_primary, True, False, True, TS, value_id=value_id)


def _link(link_id: str, source: str, golden: str) -> RecordLink:
    return RecordLink(link_id, source, golden, LinkStatus.ACTIVE, 0.9, TS, TS)


def _seed(tmp_path: Path) -> JsonFileRepository:
    repo = JsonFileRepository(tmp_path / "repo")
    repo.initialize_storage()
    gr_a = GoldenRecord(
        "GR-A",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Alice", source="SRC-A1", system="SIS", value_id="GFV-A-fn1", is_primary=True)],
            "email": [
                _value("alice@example.com", source="SRC-A1", system="SIS", value_id="GFV-A-em1", is_primary=True, trust=0.95),
                _value("a.chan@example.com", source="SRC-A2", system="CRM", value_id="GFV-A-em2", trust=0.6),
            ],
        },
        TS,
        TS,
    )
    gr_b = GoldenRecord(
        "GR-B",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Alice", source="SRC-B1", system="CRM", value_id="GFV-B-fn1", is_primary=True)],
            "phone": [_value("61234567", source="SRC-B1", system="CRM", value_id="GFV-B-ph1", is_primary=True)],
        },
        TS,
        TS,
    )
    gr_c = GoldenRecord(
        "GR-C",
        GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("AB1234567", source="SRC-C1", system="SIS", value_id="GFV-C-id1", is_primary=True)]},
        TS,
        TS,
    )
    gr_d = GoldenRecord(
        "GR-D",
        GoldenRecordStatus.ACTIVE,
        {"hkid": [_value("CD7654321", source="SRC-D1", system="CRM", value_id="GFV-D-id1", is_primary=True)]},
        TS,
        TS,
    )
    repo.save_golden_records([gr_a, gr_b, gr_c, gr_d])
    repo.save_record_links([_link("LINK-A1", "SRC-A1", "GR-A"), _link("LINK-B1", "SRC-B1", "GR-B")])
    return repo


def _client(tmp_path: Path) -> TestClient:
    _seed(tmp_path)
    return TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))


def test_compare_page_renders_field_comparison(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.get("/golden-records/compare?left=GR-A&right=GR-B")
    assert response.status_code == 200
    assert "Field Comparison" in response.text
    assert "GR-A" in response.text and "GR-B" in response.text
    assert "Preview Merge" in response.text


def test_compare_page_masks_sensitive_values(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.get("/golden-records/compare?left=GR-A&right=GR-B")
    assert "alice@example.com" not in response.text
    assert "a***e@example.com" in response.text


def test_compare_page_flags_blocked_merge(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.get("/golden-records/compare?left=GR-C&right=GR-D")
    assert response.status_code == 200
    assert "Merge blocked" in response.text


def test_merge_preview_page_renders_resulting_values(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.get("/golden-records/merge/preview?survivor=GR-A&loser=GR-B")
    assert response.status_code == 200
    assert "Resulting Primary Values" in response.text
    assert "Execute Merge" in response.text


def test_override_primary_action_redirects_and_updates(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.post(
        "/golden-records/GR-A/override-primary/email",
        data={"value_id": "GFV-A-em2", "actor": "harry", "reason": "manual"},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert response.headers["location"].startswith("/golden-records/GR-A")
    repo = client.app.state.runtime.repo
    golden = repo.find_golden_record("GR-A")
    primary = [v for v in golden.fields["email"] if v.is_primary][0]
    assert primary.value_id == "GFV-A-em2"
    assert primary.manual_lock is True


def test_merge_and_rollback_portal_flow(tmp_path: Path) -> None:
    client = _client(tmp_path)
    merge = client.post(
        "/golden-records/merge",
        data={"survivor_id": "GR-A", "loser_id": "GR-B", "actor": "harry", "reason": "dedupe"},
        follow_redirects=False,
    )
    assert merge.status_code == 303
    merge_id = merge.headers["location"].split("(")[-1].rstrip(")")
    assert merge_id.startswith("MERGE-")

    repo = client.app.state.runtime.repo
    assert repo.find_golden_record("GR-B").status == GoldenRecordStatus.SUPERSEDED

    preview = client.get(f"/golden-records/merge/{merge_id}/rollback-preview")
    assert preview.status_code == 200
    assert "Execute Rollback" in preview.text

    rollback = client.post(
        f"/golden-records/merge/{merge_id}/rollback",
        data={"actor": "harry", "reason": "undo"},
        follow_redirects=False,
    )
    assert rollback.status_code == 303
    assert repo.find_golden_record("GR-B").status == GoldenRecordStatus.ACTIVE


def test_detail_page_shows_timeline_and_override_controls(tmp_path: Path) -> None:
    client = _client(tmp_path)
    response = client.get("/golden-records/GR-A")
    assert response.status_code == 200
    assert "Timeline" in response.text
    assert "Set Primary" in response.text
