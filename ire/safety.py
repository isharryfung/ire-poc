from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .candidate_generation import CandidateBlock
from .config import IREConfig
from .deterministic import DeterministicResult
from .enums import GoldenRecordStatus, LinkStatus, SafetyFlag, SourceTrustLevel
from .models import RecordLink, SourceRecord, SourceSystem
from .scoring import ScoringResult


SENSITIVE_FIELDS = ("hkid", "phone", "email")


def mask_hkid(value: str | None) -> str | None:
    if not value:
        return value
    cleaned = str(value)
    if len(cleaned) <= 2:
        return cleaned
    return f"{cleaned[0]}{'*' * max(1, len(cleaned) - 2)}{cleaned[-1]}"


def mask_phone(value: str | None) -> str | None:
    if not value:
        return value
    digits = "".join(ch for ch in str(value) if ch.isdigit())
    if len(digits) <= 4:
        return digits
    return f"****{digits[-4:]}"


def mask_email(value: str | None) -> str | None:
    if not value or "@" not in str(value):
        return value
    local, domain = str(value).split("@", 1)
    if len(local) <= 2:
        masked_local = f"{local[:1]}*"
    else:
        masked_local = f"{local[:1]}{'*' * max(1, len(local) - 2)}{local[-1]}"
    return f"{masked_local}@{domain}"


def mask_value(field_name: str, value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, list):
        return [mask_value(field_name, item) for item in value]
    if field_name == "hkid":
        return mask_hkid(str(value))
    if field_name == "phone":
        return mask_phone(str(value))
    if field_name == "email":
        return mask_email(str(value))
    return value


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
