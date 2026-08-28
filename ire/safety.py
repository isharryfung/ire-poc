from __future__ import annotations

from dataclasses import dataclass, field

from .candidate_generation import CandidateBlock
from .config import IREConfig
from .deterministic import DeterministicResult
from .enums import GoldenRecordStatus, LinkStatus, SafetyFlag, SourceTrustLevel
from .models import RecordLink, SourceRecord, SourceSystem
from .scoring import ScoringResult


@dataclass
class SafetyCheckResult:
    blocking_flags: list[str] = field(default_factory=list)
    is_safe_for_auto_merge: bool = True
    reason: str | None = None


def check_safety(
    deterministic: DeterministicResult,
    candidates: list[CandidateBlock],
    scored_candidates: list[tuple[CandidateBlock, ScoringResult]],
    source_system: SourceSystem,
    source_record: SourceRecord,
    links: list[RecordLink],
    config: IREConfig,
) -> SafetyCheckResult:
    flags: list[str] = list(deterministic.blocking_flags)
    thresholds = config.matching_policy.thresholds

    if deterministic.conflict_detected:
        if SafetyFlag.TIER1_IDENTIFIER_CONFLICT.value not in flags and "identifier" in (deterministic.conflict_reason or ""):
            flags.append(SafetyFlag.TIER1_IDENTIFIER_CONFLICT.value)
        if SafetyFlag.DATE_OF_BIRTH_CONFLICT.value not in flags and "date_of_birth" in (deterministic.conflict_reason or ""):
            flags.append(SafetyFlag.DATE_OF_BIRTH_CONFLICT.value)

    ordered_scores = sorted(scored_candidates, key=lambda item: item[1].final_score, reverse=True)
    if len(ordered_scores) >= 2:
        top = ordered_scores[0][1].final_score
        second = ordered_scores[1][1].final_score
        if top >= thresholds.auto_merge and second >= thresholds.auto_merge:
            flags.append(SafetyFlag.MULTIPLE_HIGH_CONFIDENCE_CANDIDATES.value)
        if top - second < thresholds.candidate_gap:
            flags.append(SafetyFlag.LOW_TOP_CANDIDATE_GAP.value)

    if any(link.source_record_id == source_record.source_record_id and link.status == LinkStatus.ACTIVE for link in links):
        flags.append(SafetyFlag.SOURCE_ALREADY_LINKED_ELSEWHERE.value)

    if any(SafetyFlag.CANDIDATE_SUPERSEDED.value in candidate.blocking_reasons for candidate in candidates):
        flags.append(SafetyFlag.CANDIDATE_SUPERSEDED.value)

    if ordered_scores:
        top_score = ordered_scores[0][1]
        min_fields = int(
            config.matching_policy.safety.get(
                "min_comparable_fields",
                config.matching_policy.min_comparable_fields,
            )
        )
        if config.matching_policy.safety.get("require_minimum_fields", True) and top_score.comparable_field_count < min_fields:
            flags.append(SafetyFlag.INSUFFICIENT_EVIDENCE.value)
    elif config.matching_policy.safety.get("require_minimum_fields", True) and candidates:
        flags.append(SafetyFlag.INSUFFICIENT_EVIDENCE.value)

    if source_system.trust_level == SourceTrustLevel.LOW:
        flags.append(SafetyFlag.UNTRUSTED_SOURCE_FOR_AUTO_MERGE.value)

    deduped = list(dict.fromkeys(flags))
    return SafetyCheckResult(
        blocking_flags=deduped,
        is_safe_for_auto_merge=not deduped,
        reason=deduped[0] if deduped else None,
    )
