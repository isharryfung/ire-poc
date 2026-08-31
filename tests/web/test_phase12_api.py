from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink
from ire.web.app import create_app
from ire.web.dependencies import create_runtime

BASE = Path(__file__).resolve().parents[2]
CONFIG_DIR = BASE / "config"
TS = "2026-01-01T00:00:00Z"


def _value(raw: str, *, source: str, is_primary: bool = False, trust: float = 0.9, value_id: str) -> GoldenFieldValue:
    return GoldenFieldValue(
        raw, raw.lower(), source, "SIS", trust, is_primary, True, False, True, TS, value_id=value_id
    )


def _client(tmp_path: Path) -> TestClient:
    repo = JsonFileRepository(tmp_path / "repo")
    repo.initialize_storage()
    survivor = GoldenRecord(
        "GR-A", GoldenRecordStatus.ACTIVE,
        {
            "email": [
                _value("a@example.com", source="SRC-1", is_primary=True, value_id="GFV-a1"),
                _value("alt@example.com", source="SRC-3", value_id="GFV-a2"),
            ],
            "first_name": [_value("Alice", source="SRC-1", is_primary=True, value_id="GFV-a3")],
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
    runtime = create_runtime(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR, repo=repo)
    return TestClient(create_app(runtime=runtime))


def test_override_primary_endpoint(tmp_path: Path) -> None:
    client = _client(tmp_path)
    resp = client.post(
        "/api/v1/golden-records/GR-A/primary-values/email",
        json={"value_id": "GFV-a2", "actor": "harry", "reason": "correction"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["new_primary_value_id"] == "GFV-a2"
    values = {v["value_id"]: v for v in body["golden_record"]["all_known_values"]["email"]}
    assert values["GFV-a2"]["is_primary"] is True
    assert values["GFV-a2"]["manual_lock"] is True


def test_compare_endpoint(tmp_path: Path) -> None:
    client = _client(tmp_path)
    resp = client.get("/api/v1/golden-records/compare", params={"left": "GR-A", "right": "GR-B"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["can_merge"] is True
    statuses = {f["field_name"]: f["status"] for f in body["fields"]}
    assert statuses["first_name"] == "LEFT_ONLY"
    assert statuses["phone"] == "RIGHT_ONLY"


def test_compare_masks_sensitive_values(tmp_path: Path) -> None:
    client = _client(tmp_path)
    resp = client.get("/api/v1/golden-records/compare", params={"left": "GR-A", "right": "GR-B"})
    fields = {f["field_name"]: f for f in resp.json()["fields"]}
    assert fields["email"]["left_value"] != "a@example.com"
    assert fields["phone"]["right_value"] == "****4567"


def test_merge_preview_is_side_effect_free(tmp_path: Path) -> None:
    client = _client(tmp_path)
    state_file = tmp_path / "repo" / "state" / "golden_records.json"
    before = state_file.read_text(encoding="utf-8")
    resp = client.post(
        "/api/v1/golden-records/merge/preview",
        json={"survivor_id": "GR-A", "loser_id": "GR-B"},
    )
    assert resp.status_code == 200
    assert resp.json()["can_merge"] is True
    assert "phone" in resp.json()["merged_fields"]
    assert state_file.read_text(encoding="utf-8") == before


def test_merge_and_rollback_endpoints(tmp_path: Path) -> None:
    client = _client(tmp_path)
    merge = client.post(
        "/api/v1/golden-records/merge",
        json={"survivor_id": "GR-A", "loser_id": "GR-B", "actor": "harry", "reason": "same"},
    )
    assert merge.status_code == 200
    merge_id = merge.json()["merge_id"]
    assert merge.json()["survivor"]["golden_record_id"] == "GR-A"
    assert merge.json()["loser"]["is_superseded"] is True

    preview = client.get(f"/api/v1/golden-records/merge/{merge_id}/rollback-preview")
    assert preview.status_code == 200
    assert preview.json()["can_rollback"] is True

    rollback = client.post(
        f"/api/v1/golden-records/merge/{merge_id}/rollback",
        json={"actor": "harry", "reason": "mistake"},
    )
    assert rollback.status_code == 200
    detail = client.get("/api/v1/golden-records/GR-B")
    assert detail.json()["status"] == "ACTIVE"


def test_merge_blocked_returns_409(tmp_path: Path) -> None:
    repo = JsonFileRepository(tmp_path / "repo")
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
    runtime = create_runtime(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR, repo=repo)
    client = TestClient(create_app(runtime=runtime))
    resp = client.post(
        "/api/v1/golden-records/merge",
        json={"survivor_id": "GR-A", "loser_id": "GR-B", "actor": "harry", "reason": "x"},
    )
    assert resp.status_code == 409
    assert resp.json()["error"]["code"] == "MERGE_BLOCKED"


def test_merge_stale_version_returns_409(tmp_path: Path) -> None:
    client = _client(tmp_path)
    resp = client.post(
        "/api/v1/golden-records/merge",
        json={
            "survivor_id": "GR-A",
            "loser_id": "GR-B",
            "actor": "harry",
            "reason": "x",
            "expected_survivor_version": 99,
        },
    )
    assert resp.status_code == 409
    assert resp.json()["error"]["code"] == "STALE_VERSION"


def test_timeline_endpoint(tmp_path: Path) -> None:
    client = _client(tmp_path)
    client.post(
        "/api/v1/golden-records/GR-A/primary-values/email",
        json={"value_id": "GFV-a2", "actor": "harry", "reason": "fix"},
    )
    resp = client.get("/api/v1/golden-records/GR-A/timeline")
    assert resp.status_code == 200
    types = {e["event_type"] for e in resp.json()}
    assert "PRIMARY_OVERRIDE" in types


def test_compare_missing_returns_404(tmp_path: Path) -> None:
    client = _client(tmp_path)
    resp = client.get("/api/v1/golden-records/compare", params={"left": "GR-A", "right": "GR-X"})
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "NOT_FOUND"
