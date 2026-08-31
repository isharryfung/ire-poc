from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from ire.config import load_config
from ire.enums import ReviewStatus
from ire.models import ManualReviewTask, SourceRecord
from ire.review import ReviewDetail
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
    return TestClient(create_app(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR))


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


def test_dashboard_renders_empty_and_current_metrics(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    empty = client.get("/")
    assert empty.status_code == 200
    assert "IRE Operations Portal" in empty.text
    assert "Identity Resolution Dashboard" in empty.text
    assert "No match decisions yet." in empty.text
    assert "Phase 2" in empty.text

    repo = client.app.state.runtime.repo
    config = load_config(CONFIG_DIR)
    process_record(_seed_record(source_pk="1", emplid="E1", first_name="Alice", last_name="Chan", email="alice@example.com", phone="61234567", date_of_birth="1990-01-01"), config, repo)
    process_record(_seed_record(source_pk="2", emplid="E1", first_name="Alice", last_name="Chan", email="alice@example.com", phone="61234567", date_of_birth="1990-01-01"), config, repo)
    process_record(_seed_record(source_system="PORTAL", source_pk="3", first_name="Alice", last_name="Chan", email="alice@example.com", phone="61234567", date_of_birth="1990-01-01"), config, repo)
    process_record({"source_system": "SIS", "source_pk": "4"}, config, repo)

    populated = client.get("/")
    assert populated.status_code == 200
    assert "active golden records" in populated.text.lower()
    assert "AUTO_MERGE" in populated.text
    assert "validation failures" in populated.text.lower()
    assert "PORTAL" in populated.text


def test_identity_submission_preview_batch_page_and_review_queue_render(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    form = client.get("/identities/new")
    assert form.status_code == 200
    assert "Submit Identity" in form.text

    before = _state_snapshot(tmp_path)
    preview = client.post(
        "/identities/preview",
        data={
            "source_system": "SIS",
            "source_pk": "preview-1",
            "first_name": "John",
            "last_name": "Chan",
            "email": "john@example.com",
            "phone": "61234567",
            "date_of_birth": "1990-01-01",
        },
    )
    after = _state_snapshot(tmp_path)
    assert preview.status_code == 200
    assert "Preview Only – No Changes Made" in preview.text
    assert before == after

    submit = client.post(
        "/identities/new",
        data={
            "source_system": "SIS",
            "source_pk": "1",
            "hkid": "AB1234567",
            "emplid": "E1",
            "first_name": "John",
            "last_name": "Chan",
            "email": "john@example.com",
            "phone": "61234567",
            "date_of_birth": "1990-01-01",
            "gender": "M",
            "address": "1 Main Road",
        },
    )
    assert submit.status_code == 200
    assert "CREATE_NEW_GOLDEN" in submit.text
    assert "Match Result" in submit.text

    batch = client.post(
        "/identities/batch",
        files={"file": ("records.csv", "source_system,source_pk,emplid,first_name,last_name,email,phone,date_of_birth\nSIS,2,E2,Amy,Wong,amy@example.com,53456789,1993-01-01\nSIS,3,,Broken,Row,bad-email,123,1993-01-01", "text/csv")},
    )
    assert batch.status_code == 200
    assert "Batch Summary" in batch.text
    assert "Validation Failures" in batch.text

    repo = client.app.state.runtime.repo
    config = load_config(CONFIG_DIR)
    process_record(_seed_record(source_system="PORTAL", source_pk="4", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M"), config, repo)
    queue = client.get("/reviews")
    assert queue.status_code == 200
    assert "Review Queue" in queue.text
    assert "PORTAL" in queue.text


def test_review_detail_render_completed_actions_and_masking(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    repo = client.app.state.runtime.repo
    config = load_config(CONFIG_DIR)
    seed = process_record(_seed_record(source_pk="1", emplid="E1", hkid="AB1234567", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M", address="1 Main Road"), config, repo)
    review = process_record(_seed_record(source_system="PORTAL", source_pk="2", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M", address="1 Main Road"), config, repo)
    assert review.review_task_id is not None

    detail = client.get(f"/reviews/{review.review_task_id}")
    assert detail.status_code == 200
    assert "Field-level Evidence" in detail.text
    assert "j**n@example.com" in detail.text
    assert "****4567" in detail.text
    assert "A*******7" in detail.text
    assert "john@example.com" not in detail.text
    assert "61234567" not in detail.text

    approve = client.post(
        f"/reviews/{review.review_task_id}/approve",
        data={"reviewer": "demo", "selected_golden_record_id": seed.golden_record_id, "notes": "ok"},
    )
    assert approve.status_code == 200
    completed = approve
    assert "Review approved" in completed.text
    assert "Actions are unavailable" in completed.text
    assert "Approve Merge" not in completed.text

    golden = client.get(f"/golden-records/{seed.golden_record_id}")
    assert golden.status_code == 200
    assert "Primary Values" in golden.text
    assert "All Known Values" in golden.text
    assert "j**n@example.com" in golden.text or "j**n@example.com" in completed.text


def test_additional_pages_filters_and_masking(tmp_path: Path) -> None:
    client = _create_client(tmp_path)
    repo = client.app.state.runtime.repo
    config = load_config(CONFIG_DIR)
    process_record(_seed_record(source_pk="1", emplid="E1", first_name="Alice", last_name="Chan", email="alice@example.com", phone="61234567", date_of_birth="1990-01-01"), config, repo)
    process_record(_seed_record(source_system="PORTAL", source_pk="2", first_name="Alice", last_name="Chan", email="alice@example.com", phone="61234567", date_of_birth="1990-01-01"), config, repo)

    golden_list = client.get("/golden-records", params={"q": "Alice"})
    assert golden_list.status_code == 200
    assert "Golden Records" in golden_list.text
    assert "a***e@example.com" in golden_list.text
    assert "alice@example.com" not in golden_list.text

    pages = {
        "/record-links": "Record Links",
        "/match-history": "Match History",
        "/activity": "Activity Log",
        "/configuration/sources": "Source Systems",
        "/configuration/matching": "Matching Policy",
        "/configuration/survivorship": "Survivorship Policy",
    }
    for path, text in pages.items():
        response = client.get(path)
        assert response.status_code == 200
        assert text in response.text


def test_review_detail_shows_alternate_candidates_when_available(tmp_path: Path) -> None:
    runtime = create_runtime(root_dir=tmp_path / "repo", config_dir=CONFIG_DIR)
    app = create_app(runtime=runtime)
    client = TestClient(app)

    task = ManualReviewTask(
        review_id="REV-TEST",
        run_id="RUN-TEST",
        source_record_id="SRC-TEST",
        candidate_ids=["C1", "C2"],
        status=ReviewStatus.OPEN,
        created_at="2026-08-28T10:00:00Z",
        updated_at="2026-08-28T10:00:00Z",
        safety_flags=["LOW_TOP_CANDIDATE_GAP"],
        suggested_decision="MANUAL_REVIEW",
    )
    source = SourceRecord(
        source_record_id="SRC-TEST",
        source_system="PORTAL",
        source_pk="P-1",
        payload={"email": "person@example.com", "phone": "61234567", "first_name": "Person", "last_name": "Example"},
        payload_hash="hash",
        ingested_at="2026-08-28T10:00:00Z",
    )
    runtime.show_review_task_fn = lambda task_id, repo: ReviewDetail(
        task=task,
        source_record=source,
        normalized={"source_system": "PORTAL", "source_pk": "P-1", "email": "person@example.com", "phone": "61234567"},
        candidates=[
            {
                "candidate_id": "C1",
                "golden_record_id": "GR-1",
                "score": 0.8,
                "method": "PROBABILISTIC",
                "tier": "STRONG",
                "features": [],
                "safety_flags": [],
                "explainability": {"evidence": []},
            },
            {
                "candidate_id": "C2",
                "golden_record_id": "GR-2",
                "score": 0.79,
                "method": "PROBABILISTIC",
                "tier": "STRONG",
                "features": [],
                "safety_flags": [],
                "explainability": {"evidence": []},
            },
        ],
        safety_flags=["LOW_TOP_CANDIDATE_GAP"],
        suggested_decision="MANUAL_REVIEW",
    )

    detail = client.get("/reviews/REV-TEST")
    assert detail.status_code == 200
    assert "Alternate Candidates" in detail.text
    assert "GR-2" in detail.text
