from __future__ import annotations

import json
from pathlib import Path

from ire.config import load_config
from ire.json_repository import JsonFileRepository
from ire.service import process_batch


BASE = Path(__file__).resolve().parents[1]


def test_sample_records_run_end_to_end_and_persist_state(tmp_path: Path) -> None:
    config = load_config(BASE / "config")
    repo = JsonFileRepository(tmp_path / "repo")
    repo.initialize_storage()
    records = json.loads((BASE / "data" / "samples" / "sample_records.json").read_text(encoding="utf-8"))

    results = process_batch(records, config, repo)

    outcomes = {result.outcome for result in results}
    assert "AUTO_MERGE" in outcomes
    assert "MANUAL_REVIEW" in outcomes
    assert "CREATE_NEW_GOLDEN" in outcomes
    assert "DUPLICATE" in outcomes
    assert any(result.is_revision for result in results)

    golden_state = json.loads((tmp_path / "repo" / "state" / "golden_records.json").read_text(encoding="utf-8"))
    review_state = json.loads((tmp_path / "repo" / "state" / "review_tasks.json").read_text(encoding="utf-8"))
    links_state = json.loads((tmp_path / "repo" / "state" / "record_links.json").read_text(encoding="utf-8"))

    assert len(golden_state) >= 2
    assert len(review_state) >= 1
    assert len(links_state) >= 3
