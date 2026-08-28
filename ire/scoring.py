from __future__ import annotations

from dataclasses import dataclass, field

from .config import IREConfig
from .evidence import FieldEvidence
from .models import SourceSystem


@dataclass
class ScoringResult:
    base_score: float
    source_adjusted_score: float
    final_score: float
    evidence: list[FieldEvidence]
    comparable_field_count: int
    adjustments: list[dict] = field(default_factory=list)


def compute_score(
    evidence: list[FieldEvidence],
    source_system: SourceSystem,
    config: IREConfig,
) -> ScoringResult:
    del config
    base_score = sum(item.weighted_score for item in evidence if item.is_comparable)
    comparable_field_count = sum(1 for item in evidence if item.is_comparable)
    trust_delta = (source_system.trust_score - 0.5) * 0.1
    source_adjusted = max(0.0, min(1.0, base_score + trust_delta))
    adjustments = [
        {
            "code": "SOURCE_TRUST_ADJUSTMENT",
            "explanation": f"Adjusted by source trust score {source_system.trust_score}",
            "delta": round(trust_delta, 4),
        }
    ]
    return ScoringResult(
        base_score=round(base_score, 6),
        source_adjusted_score=round(source_adjusted, 6),
        final_score=round(source_adjusted, 6),
        evidence=evidence,
        comparable_field_count=comparable_field_count,
        adjustments=adjustments,
    )
