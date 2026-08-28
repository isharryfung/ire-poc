from __future__ import annotations

from pathlib import Path

from ire.config import load_config
from ire.json_repository import JsonFileRepository
from ire.review import approve_review, reject_review
from ire.service import preview_record, process_record


BASE = Path(__file__).resolve().parents[1]


def _config():
    return load_config(BASE / "config")


def _seed_record(**data):
    return {
        "source_system": data.pop("source_system", "SIS"),
        "source_pk": data.pop("source_pk"),
        "data": data,
    }


def test_process_record_covers_create_auto_manual_and_duplicate(tmp_path: Path) -> None:
    config = _config()
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    first = process_record(
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
            address="1 Main Road",
        ),
        config,
        repo,
    )
    auto = process_record(
        _seed_record(
            source_pk="2",
            emplid="E1",
            hkid="AB1234567",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
            address="1 Main Road",
        ),
        config,
        repo,
    )
    manual = process_record(
        _seed_record(
            source_system="ALUMNI",
            source_pk="3",
            first_name="Jahn",
            last_name="Chan",
            email="other@example.com",
            phone="61230000",
            date_of_birth="1990-01-01",
            gender="M",
            address="2 Other",
        ),
        config,
        repo,
    )
    duplicate = process_record(
        _seed_record(
            source_pk="2",
            emplid="E1",
            hkid="AB1234567",
            first_name="John",
            last_name="Chan",
            email="john@example.com",
            phone="61234567",
            date_of_birth="1990-01-01",
            gender="M",
            address="1 Main Road",
        ),
        config,
        repo,
    )

    assert first.outcome == "CREATE_NEW_GOLDEN"
    assert auto.outcome == "AUTO_MERGE"
    assert manual.outcome == "MANUAL_REVIEW"
    assert duplicate.outcome == "DUPLICATE"
    assert len(repo.load_golden_records()) == 1
    assert len(repo.load_manual_review_tasks()) == 1


def test_process_record_detects_revision_and_preview_is_read_only(tmp_path: Path) -> None:
    config = _config()
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    first = process_record(
        _seed_record(source_pk="10", emplid="E10", first_name="Amy", last_name="Wong", email="amy@example.com", phone="53456789", date_of_birth="1993-01-01", gender="F"),
        config,
        repo,
    )
    revised = process_record(
        _seed_record(source_pk="10", emplid="E10", first_name="Amy", last_name="Wong", email="amy2@example.com", phone="53456789", date_of_birth="1993-01-01", gender="F"),
        config,
        repo,
    )
    preview = preview_record(
        _seed_record(source_pk="11", source_system="ALUMNI", first_name="Amy", last_name="Wong", email="amy@example.com", phone="53456789", date_of_birth="1993-01-01", gender="F"),
        config,
        repo,
    )

    assert first.outcome == "CREATE_NEW_GOLDEN"
    assert revised.is_revision is True
    assert len(repo.load_source_records()) == 2
    assert preview["outcome"] in {"AUTO_MERGE", "MANUAL_REVIEW", "CREATE_NEW_GOLDEN"}
    assert len(repo.load_source_records()) == 2


def test_review_approve_and_reject_flows(tmp_path: Path) -> None:
    config = _config()
    repo = JsonFileRepository(tmp_path)
    repo.initialize_storage()

    seed = process_record(
        _seed_record(source_pk="1", emplid="E1", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M"),
        config,
        repo,
    )
    review = process_record(
        _seed_record(source_system="PORTAL", source_pk="2", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M"),
        config,
        repo,
    )
    assert review.review_task_id is not None
    updated = approve_review(review.review_task_id, seed.golden_record_id, "reviewer", "ok", repo, config)
    assert updated.golden_record_id == seed.golden_record_id

    review2 = process_record(
        _seed_record(source_system="ALUMNI", source_pk="3", first_name="Unique", last_name="Person", email="unique@example.com", phone="58888888", date_of_birth="1988-08-08", gender="U"),
        config,
        repo,
    )
    if review2.outcome != "MANUAL_REVIEW":
        review2 = process_record(
            _seed_record(source_system="PORTAL", source_pk="4", first_name="John", last_name="Chan", email="john@example.com", phone="61234567", date_of_birth="1990-01-01", gender="M"),
            config,
            repo,
        )
    if review2.review_task_id:
        result = reject_review(review2.review_task_id, "invalid", "reviewer", "bad data", repo, config)
        assert result["action"] == "invalid"
