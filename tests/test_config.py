import json
from pathlib import Path

import pytest

from ire.config import load_config
from ire.exceptions import ConfigurationError


def test_load_config_success() -> None:
    config_dir = Path(__file__).resolve().parents[1] / "config"
    config = load_config(config_dir)
    assert len(config.source_systems) >= 1
    assert config.matching_policy.thresholds.auto_merge == 0.85


def test_load_config_missing_required_file(tmp_path: Path) -> None:
    (tmp_path / "source_systems.json").write_text("[]", encoding="utf-8")
    with pytest.raises(ConfigurationError):
        load_config(tmp_path)


def test_load_config_invalid_threshold(tmp_path: Path) -> None:
    (tmp_path / "source_systems.json").write_text(
        json.dumps(
            [
                {
                    "code": "A",
                    "name": "A",
                    "trust_level": "HIGH",
                    "trust_score": 0.9,
                    "internal": True,
                    "active": True,
                }
            ]
        ),
        encoding="utf-8",
    )
    (tmp_path / "matching_policy.json").write_text(
        json.dumps(
            {
                "version": "1",
                "thresholds": {"auto_merge": 0.4, "manual_review": 0.5, "candidate_gap": 0.1},
                "field_priorities": ["email"],
                "algorithms": ["exact_match"],
                "candidate_limit": 10,
                "safety": {},
            }
        ),
        encoding="utf-8",
    )
    (tmp_path / "survivorship_policy.json").write_text(
        json.dumps(
            {"version": "1", "protect_manual_lock": True, "field_strategies": {"email": "x"}}
        ),
        encoding="utf-8",
    )
    with pytest.raises(ConfigurationError):
        load_config(tmp_path)
