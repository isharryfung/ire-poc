from __future__ import annotations

import json
from pathlib import Path

from fastapi.testclient import TestClient

from ire.config import load_config
from ire.exceptions import ConfigurationError, RepositoryError
from ire.service import process_record
from ire.web.app import create_app
from ire.web.dependencies import create_runtime

BASE = Path(__file__).resolve().parents[2]
CONFIG_DIR = BASE / "config"


def _seed_record(**data):
    return {
        "source_system": data.pop("source_system", "SIS"),
        "source_pk": data.pop("source_pk"),
        "data": data,
    }


def _create_client(tmp_path: Path) -> TestClient:
    app = create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR)
    return TestClient(app)


def _state_snapshot(root: Path) -> dict[str, str]:
    repo_root = root / "repo"
    paths = [
        repo_root / "state" / "golden_records.json",
        repo_root / "state" / "record_links.json",
        repo_root / "state" / "review_tasks.json",
        repo_root / "events" / "source_records.jsonl",
        repo_root / "events" / "match_runs.jsonl",
        repo_root / "events" / "merge_history.jsonl",
        repo_root / "events" / "audit_log.jsonl",
    ]
    return {path.name: path.read_text(encoding="utf-8") for path in paths}


def _runtime_repo(client: TestClient):
    return client.app.state.runtime.repo


def test_health_process_validation_and_preview_side_effects(tmp_path: Path) -> None:
    client = _create_client(tmp_path)

    health = client.get("/api/v1/health")
    assert health.status_code == 200
    assert health.json()["database"] is False
    assert health.json()["production_ready"] is False
    docs = client.get("/docs")
    assert docs.status_code == 200
    assert "Swagger UI" in docs.text

    valid = client.post(
        "/api/v1/identities/process",
        json=_seed_record(
            source_pk="1",
            emplid="E1",
            hkid="AB1234567",
            first_name="Siu Mei",
            last_name="Chan",
            email="smchan@example.edu",
            phone="85674123",
            date_of_birth="1985-05-20",
        ),
    )
    assert valid.status_code == 200
    assert valid.json()["outcome"] == "CREATE_NEW_GOLDEN"
    assert valid.json()["match_run_id"]

    invalid = client.post("/api/v1/identities/process", json={"source_system": "SIS", "source_pk": "2"})
    assert invalid.status_code == 422
    assert invalid.json()["error"]["code"] == "VALIDATION_ERROR"

    before = _state_snapshot(tmp_path)
    preview = client.post(
        "/api/v1/identities/preview",
        json=_seed_record(
            source_system="PORTAL",
            source_pk="3",
            first_name="Siu Mei",
            last_name="Chan",
            email="smchan@example.edu",
            phone="85674123",
            date_of_birth="1985-05-20",
        ),
    )
    after = _state_snapshot(tmp_path)
    assert preview.status_code == 200
    assert preview.json()["outcome"] == "MANUAL_REVIEW"
    assert before == after


def test_batch_upload_and_golden_endpoints(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    csv_payload = "\n".join(
        [
            "source_system,source_pk,emplid,first_name,last_name,email,phone,date_of_birth",
            "SIS,1,E1,Alice,Chan,alice@example.com,61234567,1990-01-01",
            "SIS,2,,Broken,Row,bad-email,123,1990-01-01",
        ]
    )
    response = client.post(
        "/api/v1/identities/batch",
        files={"file": ("records.csv", csv_payload, "text/csv")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["total_records"] == 2
    assert body["processed_count"] == 2
    assert body["validation_failure_count"] == 1
    assert any(item["outcome"] == "CREATE_NEW_GOLDEN" for item in body["results"])

    listing = client.get("/api/v1/golden-records")
    assert listing.status_code == 200
    assert len(listing.json()) == 1
    golden_id = listing.json()[0]["golden_record_id"]
    assert listing.json()[0]["primary_values"]["email"]["raw_value"] == "a***e@example.com"

    detail = client.get(f"/api/v1/golden-records/{golden_id}")
    assert detail.status_code == 200
    assert detail.json()["golden_record_id"] == golden_id

    missing = client.get("/api/v1/golden-records/GR-MISSING")
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "NOT_FOUND"


def test_review_list_detail_and_decisions(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    repo = _runtime_repo(client)
    config = load_config(CONFIG_DIR)

    seed = process_record(
        _seed_record(
            source_pk="1",
            emplid="E1",
            hkid="AB1234567",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
        ),
        config,
        repo,
    )
    review = process_record(
        _seed_record(
            source_system="PORTAL",
            source_pk="2",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
        ),
        config,
        repo,
    )
    assert review.review_task_id is not None

    listing = client.get("/api/v1/reviews", params={"status": "OPEN", "source_system": "PORTAL"})
    assert listing.status_code == 200
    assert listing.json()[0]["task_id"] == review.review_task_id

    detail = client.get(f"/api/v1/reviews/{review.review_task_id}")
    assert detail.status_code == 200
    assert detail.json()["best_candidate"]["golden_record_id"] == seed.golden_record_id
    assert detail.json()["source_record"]["payload"]["email"] == "j**n@example.com"

    approve = client.post(
        f"/api/v1/reviews/{review.review_task_id}/approve",
        json={"reviewer": "demo", "selected_golden_record_id": seed.golden_record_id, "notes": "looks right"},
    )
    assert approve.status_code == 200
    assert approve.json()["decision"] == "APPROVE_MERGE"

    conflict = client.post(
        f"/api/v1/reviews/{review.review_task_id}/approve",
        json={"reviewer": "demo", "selected_golden_record_id": seed.golden_record_id},
    )
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "REVIEW_CONFLICT"

    second_review = process_record(
        _seed_record(
            source_system="PORTAL",
            source_pk="3",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
        ),
        config,
        repo,
    )
    assert second_review.review_task_id is not None
    reject_new = client.post(
        f"/api/v1/reviews/{second_review.review_task_id}/reject",
        json={"reviewer": "demo", "action": "create-new", "notes": "separate person"},
    )
    assert reject_new.status_code == 200
    assert reject_new.json()["action"] == "create-new"
    assert reject_new.json()["golden_record_id"]

    third_review = process_record(
        _seed_record(
            source_system="PORTAL",
            source_pk="4",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
        ),
        config,
        repo,
    )
    assert third_review.review_task_id is not None
    reject_invalid = client.post(
        f"/api/v1/reviews/{third_review.review_task_id}/reject",
        json={"reviewer": "demo", "action": "invalid", "notes": "bad payload"},
    )
    assert reject_invalid.status_code == 200
    assert reject_invalid.json()["action"] == "invalid"


def test_structured_storage_and_configuration_errors(tmp_path: Path) -> None:
    runtime = create_runtime(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR)
    runtime.process_record_fn = lambda raw, config, repo: (_ for _ in ()).throw(RepositoryError("boom"))
    app = create_app(runtime=runtime)
    client = TestClient(app)
    storage = client.post("/api/v1/identities/process", json=_seed_record(source_pk="1"))
    assert storage.status_code == 500
    assert storage.json()["error"]["code"] == "STORAGE_ERROR"

    runtime = create_runtime(root_dir=tmp_path / "repo2", config_dir=CONFIG_DIR)
    runtime.process_record_fn = lambda raw, config, repo: (_ for _ in ()).throw(ConfigurationError("bad config path /tmp/secret"))
    app = create_app(runtime=runtime)
    client = TestClient(app)
    config_error = client.post("/api/v1/identities/process", json=_seed_record(source_pk="1"))
    assert config_error.status_code == 500
    assert config_error.json()["error"]["code"] == "CONFIGURATION_ERROR"
    assert "/tmp/secret" not in json.dumps(config_error.json())
