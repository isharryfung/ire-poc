from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .config import IREConfig
from .models import GoldenFieldValue
from .normalization import normalize_address
from .similarity import (
    address_jaccard,
    dob_similarity,
    email_similarity,
    exact_match,
    gender_similarity,
    name_similarity,
    phone_similarity,
)


@dataclass
class FieldEvidence:
    field: str
    algorithm: str
    raw_a: str | None
    raw_b: str | None
    normalized_a: str | None
    normalized_b: str | None
    similarity: float
    priority: int
    weight: float
    normalized_weight: float
    weighted_score: float
    is_comparable: bool
    is_match: bool
    is_conflict: bool
    explanation: str


_FIELD_WEIGHTS = {
    "hkid": 10.0,
    "emplid": 9.0,
    "student_id": 8.0,
    "alumni_id": 8.0,
    "email": 7.0,
    "phone": 6.0,
    "first_name": 4.0,
    "last_name": 5.0,
    "date_of_birth": 5.0,
    "gender": 2.0,
    "address": 3.0,
}


def _primary_value(values: list[GoldenFieldValue]) -> GoldenFieldValue | None:
    for value in values:
        if value.is_primary and value.is_active:
            return value
    return values[0] if values else None


def build_evidence(
    incoming_normalized: dict[str, Any],
    golden_fields: dict[str, list[GoldenFieldValue]],
    config: IREConfig,
) -> list[FieldEvidence]:
    evidences: list[FieldEvidence] = []

    for priority, field_name in enumerate(config.matching_policy.field_priorities, start=1):
        weight = _FIELD_WEIGHTS.get(field_name, 1.0)
        incoming_value = incoming_normalized.get(field_name)
        primary = _primary_value(golden_fields.get(field_name, []))
        existing_value = primary.normalized_value if primary is not None else None
        raw_b = primary.raw_value if primary is not None else None
        raw_a = None if incoming_value is None else str(incoming_value)

        algorithm = "exact_match"
        similarity = 0.0
        comparable = incoming_value is not None and existing_value is not None

        if field_name in {"hkid", "emplid", "student_id", "alumni_id"}:
            similarity = exact_match(incoming_value, existing_value)
        elif field_name == "email":
            algorithm = "email_composite"
            similarity = email_similarity(incoming_value, existing_value)
        elif field_name == "phone":
            algorithm = "phone_exact_last8"
            similarity = phone_similarity(incoming_normalized.get("phone_digits") or incoming_value, existing_value)
        elif field_name in {"first_name", "last_name"}:
            algorithm = "jaro_winkler"
            similarity = name_similarity(incoming_value, existing_value)
        elif field_name == "date_of_birth":
            similarity = dob_similarity(incoming_value, existing_value)
        elif field_name == "gender":
            similarity = gender_similarity(incoming_value, existing_value)
        elif field_name == "address":
            algorithm = "token_jaccard"
            incoming_tokens = incoming_normalized.get("address_tokens") or normalize_address(incoming_value)
            existing_tokens = normalize_address(existing_value)
            similarity = address_jaccard(incoming_tokens, existing_tokens)
            raw_a = None if incoming_value is None else str(incoming_value)
        else:
            similarity = exact_match(incoming_value, existing_value)

        evidences.append(
            FieldEvidence(
                field=field_name,
                algorithm=algorithm,
                raw_a=raw_a,
                raw_b=raw_b,
                normalized_a=None if incoming_value is None else str(incoming_value),
                normalized_b=existing_value,
                similarity=similarity if comparable else 0.0,
                priority=priority,
                weight=weight,
                normalized_weight=0.0,
                weighted_score=0.0,
                is_comparable=comparable,
                is_match=comparable and similarity >= 0.85,
                is_conflict=comparable and field_name in {"hkid", "emplid", "student_id", "alumni_id", "date_of_birth"} and similarity == 0.0,
                explanation=f"{field_name} compared using {algorithm}",
            )
        )

    comparable_weight = sum(item.weight for item in evidences if item.is_comparable)
    normalized: list[FieldEvidence] = []
    for item in evidences:
        normalized_weight = item.weight / comparable_weight if item.is_comparable and comparable_weight else 0.0
        normalized.append(
            FieldEvidence(
                field=item.field,
                algorithm=item.algorithm,
                raw_a=item.raw_a,
                raw_b=item.raw_b,
                normalized_a=item.normalized_a,
                normalized_b=item.normalized_b,
                similarity=item.similarity,
                priority=item.priority,
                weight=item.weight,
                normalized_weight=normalized_weight,
                weighted_score=item.similarity * normalized_weight,
                is_comparable=item.is_comparable,
                is_match=item.is_match,
                is_conflict=item.is_conflict,
                explanation=item.explanation,
            )
        )
    return normalized
