from __future__ import annotations

from dataclasses import dataclass, field

from .candidate_generation import CandidateBlock
from .config import IREConfig
from .deterministic import DeterministicResult
from .enums import MatchDecisionType, SafetyFlag
from .safety import SafetyCheckResult
from .scoring import ScoringResult
from .validation import ValidationResult


@dataclass
class DecisionResult:
    outcome: str
    golden_record_id: str | None
    confidence: float
    reason: str
    safety_flags: list[str] = field(default_factory=list)


def make_decision(
    validation: ValidationResult,
    deterministic: DeterministicResult | None,
    candidates: list[CandidateBlock],
    scored_candidates: list[tuple[CandidateBlock, ScoringResult]] | None,
    safety: SafetyCheckResult | None,
    config: IREConfig,
) -> DecisionResult:
    thresholds = config.matching_policy.thresholds
    safety_flags = list(safety.blocking_flags) if safety is not None else []

    if not validation.valid:
        return DecisionResult(MatchDecisionType.VALIDATION_FAILED.value, None, 0.0, "validation failed", safety_flags)

    if deterministic is not None and deterministic.conflict_detected:
        return DecisionResult(MatchDecisionType.MANUAL_REVIEW.value, deterministic.golden_record_id, 1.0, deterministic.conflict_reason or "deterministic conflict", safety_flags)

    if deterministic is not None and deterministic.matched:
        if safety is None or safety.is_safe_for_auto_merge:
            return DecisionResult(MatchDecisionType.AUTO_MERGE.value, deterministic.golden_record_id, 1.0, "deterministic match", safety_flags)
        return DecisionResult(MatchDecisionType.MANUAL_REVIEW.value, deterministic.golden_record_id, 1.0, "deterministic match blocked by safety", safety_flags)

    if not candidates or not scored_candidates:
        return DecisionResult(MatchDecisionType.CREATE_NEW_GOLDEN.value, None, 0.0, "no viable candidates", safety_flags)

    ordered = sorted(scored_candidates, key=lambda item: item[1].final_score, reverse=True)
    top_candidate, top_score = ordered[0]
    top_gap = top_score.final_score - ordered[1][1].final_score if len(ordered) > 1 else 1.0

    if any(flag in safety_flags for flag in (SafetyFlag.MULTIPLE_HIGH_CONFIDENCE_CANDIDATES.value, SafetyFlag.LOW_TOP_CANDIDATE_GAP.value)):
        return DecisionResult(MatchDecisionType.MANUAL_REVIEW.value, top_candidate.golden_record_id, top_score.final_score, "multiple strong candidates require review", safety_flags)

    if top_score.final_score >= thresholds.auto_merge and (safety is None or safety.is_safe_for_auto_merge):
        return DecisionResult(MatchDecisionType.AUTO_MERGE.value, top_candidate.golden_record_id, top_score.final_score, "probabilistic threshold met", safety_flags)

    if top_score.final_score >= thresholds.manual_review:
        return DecisionResult(MatchDecisionType.MANUAL_REVIEW.value, top_candidate.golden_record_id, top_score.final_score, f"score {top_score.final_score:.2f} requires review", safety_flags)

    if top_gap < thresholds.candidate_gap:
        return DecisionResult(MatchDecisionType.MANUAL_REVIEW.value, top_candidate.golden_record_id, top_score.final_score, "top candidate gap too low", safety_flags)

    return DecisionResult(MatchDecisionType.CREATE_NEW_GOLDEN.value, None, top_score.final_score, "score below manual review threshold", safety_flags)
