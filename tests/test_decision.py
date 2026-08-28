from __future__ import annotations

from ire.candidate_generation import CandidateBlock
from ire.config import load_config
from ire.decision import make_decision
from ire.deterministic import DeterministicResult
from ire.safety import SafetyCheckResult
from ire.scoring import ScoringResult
from ire.validation import ValidationResult


def _config():
    from pathlib import Path

    return load_config(Path(__file__).resolve().parents[1] / "config")


def test_invalid_record_returns_validation_failed() -> None:
    decision = make_decision(ValidationResult(False, [], {}), None, [], None, None, _config())
    assert decision.outcome == "VALIDATION_FAILED"


def test_score_boundaries_follow_policy() -> None:
    config = _config()
    validation = ValidationResult(True, [], {})
    candidate = CandidateBlock("GR-1", ["exact_email"])

    auto = make_decision(
        validation,
        None,
        [candidate],
        [(candidate, ScoringResult(0.85, 0.85, 0.85, [], 3, []))],
        SafetyCheckResult([], True, None),
        config,
    )
    manual = make_decision(
        validation,
        None,
        [candidate],
        [(candidate, ScoringResult(0.50, 0.50, 0.50, [], 3, []))],
        SafetyCheckResult([], True, None),
        config,
    )
    new = make_decision(
        validation,
        None,
        [candidate],
        [(candidate, ScoringResult(0.49, 0.49, 0.49, [], 3, []))],
        SafetyCheckResult([], True, None),
        config,
    )

    assert auto.outcome == "AUTO_MERGE"
    assert manual.outcome == "MANUAL_REVIEW"
    assert new.outcome == "CREATE_NEW_GOLDEN"


def test_low_gap_forces_manual_review() -> None:
    config = _config()
    validation = ValidationResult(True, [], {})
    c1 = CandidateBlock("GR-1", ["x"])
    c2 = CandidateBlock("GR-2", ["y"])
    decision = make_decision(
        validation,
        DeterministicResult(False, None, False, None, []),
        [c1, c2],
        [
            (c1, ScoringResult(0.9, 0.9, 0.9, [], 3, [])),
            (c2, ScoringResult(0.86, 0.86, 0.86, [], 3, [])),
        ],
        SafetyCheckResult(["LOW_TOP_CANDIDATE_GAP"], False, "LOW_TOP_CANDIDATE_GAP"),
        config,
    )
    assert decision.outcome == "MANUAL_REVIEW"
