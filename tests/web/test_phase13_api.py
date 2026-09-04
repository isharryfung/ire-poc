from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from ire.config import load_config
from ire.service import process_record
from ire.web.app import create_app

BASE = Path(__file__).resolve().parents[2]
CONFIG_DIR = BASE / "config"


def _seed_record(**data):
    return {
        "source_system": data.pop("source_system", "SIS"),
        "source_pk": data.pop("source_pk"),
        "data": data,
    }


def test_phase13_api_endpoints(tmp_path: Path) -> None:
    client = TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))
    runtime = client.app.state.runtime
    config = load_config(CONFIG_DIR)
    process_record(
        _seed_record(
            source_pk="1",
            emplid="E1",
            first_name="Alice",
            last_name="Chan",
            email="alice@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
        ),
        config,
        runtime.repo,
    )
    process_record(
        _seed_record(
            source_pk="2",
            emplid="E2",
            first_name="Betty",
            last_name="Chan",
            email="betty@example.com",
            phone="53456789",
            date_of_birth="1990-01-01",
        ),
        config,
        runtime.repo,
    )
    scan = client.post("/api/v1/duplicates/scan")
    assert scan.status_code == 200
    listing = client.get("/api/v1/duplicates")
    assert listing.status_code == 200
    integrity = client.get("/api/v1/integrity/check")
    assert integrity.status_code == 200
    quality = client.get("/api/v1/data-quality")
    assert quality.status_code == 200
    export = client.get("/api/v1/exports/data-quality", params={"format": "json"})
    assert export.status_code == 200
    assert export.headers["content-disposition"]
    if listing.json():
        candidate_id = listing.json()[0]["candidate_id"]
        invalid_update = client.post(
            f"/api/v1/duplicates/{candidate_id}/status",
            json={"status": "NOT_DUPLICATE"},
        )
        assert invalid_update.status_code == 400
        assert invalid_update.json()["error"]["code"] == "VALIDATION_ERROR"


def test_governance_pages_render(tmp_path: Path) -> None:
    client = TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))
    for path, text in {
        "/duplicates": "Potential Duplicates",
        "/integrity": "Storage Integrity",
        "/data-quality": "Data Quality Dashboard",
    }.items():
        response = client.get(path)
        assert response.status_code == 200
        assert text in response.text


def test_duplicate_detail_and_status_update_pages(tmp_path: Path) -> None:
    client = TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))
    runtime = client.app.state.runtime
    config = load_config(CONFIG_DIR)
    process_record(
        _seed_record(
            source_pk="1",
            emplid="E1",
            first_name="Alice",
            last_name="Chan",
            email="alice@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
        ),
        config,
        runtime.repo,
    )
    process_record(
        _seed_record(
            source_pk="2",
            emplid="E2",
            first_name="Betty",
            last_name="Chan",
            email="betty@example.com",
            phone="53456789",
            date_of_birth="1990-01-01",
        ),
        config,
        runtime.repo,
    )
    client.post("/api/v1/duplicates/scan")
    candidate_id = client.get("/api/v1/duplicates").json()[0]["candidate_id"]
    detail = client.get(f"/duplicates/{candidate_id}")
    assert detail.status_code == 200
    assert candidate_id in detail.text

    invalid = client.post(
        f"/duplicates/{candidate_id}/status",
        data={"status": "NOT_DUPLICATE", "actor": "", "reason": ""},
        follow_redirects=True,
    )
    assert invalid.status_code == 200
    assert "actor and reason are required" in invalid.text

    valid = client.post(
        f"/duplicates/{candidate_id}/status",
        data={"status": "IN_REVIEW", "actor": "demo", "reason": "triage"},
        follow_redirects=True,
    )
    assert valid.status_code == 200
    assert "Status updated" in valid.text


def test_export_csv_formula_protection_api(tmp_path: Path) -> None:
    client = TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))
    runtime = client.app.state.runtime
    config = load_config(CONFIG_DIR)
    process_record(
        _seed_record(
            source_pk="FORMULA-1",
            emplid="E-F1",
            first_name="Formula",
            last_name="Case",
            email="=cmd@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
        ),
        config,
        runtime.repo,
    )
    response = client.get("/api/v1/exports/golden-records", params={"format": "csv"})
    assert response.status_code == 200
    body = response.content.decode("utf-8")
    assert "schema_version" in body
    assert "'=cmd@example.com" in body or "=cmd@example.com" not in body
