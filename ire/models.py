from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Any

from .enums import (
    GoldenRecordStatus,
    IngestStatus,
    LinkStatus,
    MatchDecisionType,
    MatchMethod,
    MatchTier,
    MergeEventType,
    ReviewDecisionType,
    ReviewStatus,
    SafetyFlag,
    SourceTrustLevel,
)
from .exceptions import ValidationError
from .ids import utc_now_iso


def _require_probability(name: str, value: float) -> None:
    if not (0.0 <= value <= 1.0):
        raise ValidationError(f"{name} must be between 0.0 and 1.0, got {value}")


def _require_utc_z_timestamp(name: str, value: str) -> None:
    if not value.endswith("Z"):
        raise ValidationError(f"{name} must be UTC ISO-8601 ending with Z, got {value}")
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValidationError(f"{name} is not a valid timestamp: {value}") from exc


def _serialize(obj: Any) -> Any:
    if hasattr(obj, "to_dict"):
        return obj.to_dict()
    if isinstance(obj, list):
        return [_serialize(item) for item in obj]
    if isinstance(obj, dict):
        return {k: _serialize(v) for k, v in obj.items()}
    if hasattr(obj, "value"):
        return obj.value
    return obj


@dataclass(frozen=True)
class SourceSystem:
    code: str
    name: str
    trust_level: SourceTrustLevel
    trust_score: float
    internal: bool
    active: bool

    def __post_init__(self) -> None:
        _require_probability("trust_score", self.trust_score)

    def to_dict(self) -> dict[str, Any]:
        return {
            "code": self.code,
            "name": self.name,
            "trust_level": self.trust_level.value,
            "trust_score": self.trust_score,
            "internal": self.internal,
            "active": self.active,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SourceSystem":
        return cls(
            code=data["code"],
            name=data["name"],
            trust_level=SourceTrustLevel(data["trust_level"]),
            trust_score=float(data["trust_score"]),
            internal=bool(data["internal"]),
            active=bool(data["active"]),
        )


@dataclass(frozen=True)
class SourceRecord:
    source_record_id: str
    source_system: str
    source_pk: str
    payload: dict[str, Any]
    payload_hash: str
    ingested_at: str = field(default_factory=utc_now_iso)
    ingest_status: IngestStatus = IngestStatus.RECEIVED
    supersedes_source_record_id: str | None = None

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("ingested_at", self.ingested_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "source_record_id": self.source_record_id,
            "source_system": self.source_system,
            "source_pk": self.source_pk,
            "payload": self.payload,
            "payload_hash": self.payload_hash,
            "ingested_at": self.ingested_at,
            "ingest_status": self.ingest_status.value,
            "supersedes_source_record_id": self.supersedes_source_record_id,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SourceRecord":
        return cls(
            source_record_id=data["source_record_id"],
            source_system=data["source_system"],
            source_pk=data["source_pk"],
            payload=dict(data["payload"]),
            payload_hash=data["payload_hash"],
            ingested_at=data["ingested_at"],
            ingest_status=IngestStatus(data["ingest_status"]),
            supersedes_source_record_id=data.get("supersedes_source_record_id"),
        )


@dataclass(frozen=True)
class NormalizedIdentity:
    full_name: str | None = None
    given_name: str | None = None
    family_name: str | None = None
    email: str | None = None
    phone: str | None = None
    date_of_birth: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "NormalizedIdentity":
        return cls(**data)


@dataclass(frozen=True)
class GoldenFieldValue:
    raw_value: str
    normalized_value: str | None
    source_record_id: str
    source_system: str
    trust_score: float
    is_primary: bool
    is_verified: bool
    manual_lock: bool
    is_active: bool
    observed_at: str
    valid_from: str | None = None
    valid_to: str | None = None

    def __post_init__(self) -> None:
        _require_probability("trust_score", self.trust_score)
        _require_utc_z_timestamp("observed_at", self.observed_at)
        if self.valid_from is not None:
            _require_utc_z_timestamp("valid_from", self.valid_from)
        if self.valid_to is not None:
            _require_utc_z_timestamp("valid_to", self.valid_to)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "GoldenFieldValue":
        return cls(**data)


@dataclass(frozen=True)
class GoldenRecord:
    golden_record_id: str
    status: GoldenRecordStatus
    fields: dict[str, list[GoldenFieldValue]]
    created_at: str
    updated_at: str
    superseded_by: str | None = None

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)
        _require_utc_z_timestamp("updated_at", self.updated_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "golden_record_id": self.golden_record_id,
            "status": self.status.value,
            "fields": {k: [_serialize(v) for v in values] for k, values in self.fields.items()},
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "superseded_by": self.superseded_by,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "GoldenRecord":
        return cls(
            golden_record_id=data["golden_record_id"],
            status=GoldenRecordStatus(data["status"]),
            fields={
                k: [GoldenFieldValue.from_dict(item) for item in values]
                for k, values in dict(data["fields"]).items()
            },
            created_at=data["created_at"],
            updated_at=data["updated_at"],
            superseded_by=data.get("superseded_by"),
        )


@dataclass(frozen=True)
class RecordLink:
    link_id: str
    source_record_id: str
    golden_record_id: str
    status: LinkStatus
    confidence: float
    created_at: str
    updated_at: str

    def __post_init__(self) -> None:
        _require_probability("confidence", self.confidence)
        _require_utc_z_timestamp("created_at", self.created_at)
        _require_utc_z_timestamp("updated_at", self.updated_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "link_id": self.link_id,
            "source_record_id": self.source_record_id,
            "golden_record_id": self.golden_record_id,
            "status": self.status.value,
            "confidence": self.confidence,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "RecordLink":
        return cls(
            link_id=data["link_id"],
            source_record_id=data["source_record_id"],
            golden_record_id=data["golden_record_id"],
            status=LinkStatus(data["status"]),
            confidence=float(data["confidence"]),
            created_at=data["created_at"],
            updated_at=data["updated_at"],
        )


@dataclass(frozen=True)
class MatchFeature:
    name: str
    value: float
    weight: float
    evidence: str | None = None

    def __post_init__(self) -> None:
        _require_probability("value", self.value)
        _require_probability("weight", self.weight)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MatchFeature":
        return cls(**data)


@dataclass(frozen=True)
class MatchCandidate:
    candidate_id: str
    golden_record_id: str
    score: float
    method: MatchMethod
    tier: MatchTier
    features: list[MatchFeature]
    safety_flags: list[SafetyFlag]
    explainability: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        _require_probability("score", self.score)

    def to_dict(self) -> dict[str, Any]:
        return {
            "candidate_id": self.candidate_id,
            "golden_record_id": self.golden_record_id,
            "score": self.score,
            "method": self.method.value,
            "tier": self.tier.value,
            "features": [_serialize(feature) for feature in self.features],
            "safety_flags": [flag.value for flag in self.safety_flags],
            "explainability": self.explainability,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MatchCandidate":
        return cls(
            candidate_id=data["candidate_id"],
            golden_record_id=data["golden_record_id"],
            score=float(data["score"]),
            method=MatchMethod(data["method"]),
            tier=MatchTier(data["tier"]),
            features=[MatchFeature.from_dict(item) for item in data["features"]],
            safety_flags=[SafetyFlag(item) for item in data.get("safety_flags", [])],
            explainability=dict(data.get("explainability", {})),
        )


@dataclass(frozen=True)
class MatchRun:
    run_id: str
    source_record_id: str
    candidates: list[MatchCandidate]
    best_candidate_id: str | None
    decision: MatchDecisionType
    policy_version: str
    created_at: str

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "run_id": self.run_id,
            "source_record_id": self.source_record_id,
            "candidates": [_serialize(candidate) for candidate in self.candidates],
            "best_candidate_id": self.best_candidate_id,
            "decision": self.decision.value,
            "policy_version": self.policy_version,
            "created_at": self.created_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MatchRun":
        return cls(
            run_id=data["run_id"],
            source_record_id=data["source_record_id"],
            candidates=[MatchCandidate.from_dict(item) for item in data["candidates"]],
            best_candidate_id=data.get("best_candidate_id"),
            decision=MatchDecisionType(data["decision"]),
            policy_version=data["policy_version"],
            created_at=data["created_at"],
        )


@dataclass(frozen=True)
class MatchDecision:
    decision_id: str
    run_id: str
    decision: MatchDecisionType
    tier: MatchTier
    confidence: float
    reason: str
    created_at: str

    def __post_init__(self) -> None:
        _require_probability("confidence", self.confidence)
        _require_utc_z_timestamp("created_at", self.created_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "decision_id": self.decision_id,
            "run_id": self.run_id,
            "decision": self.decision.value,
            "tier": self.tier.value,
            "confidence": self.confidence,
            "reason": self.reason,
            "created_at": self.created_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MatchDecision":
        return cls(
            decision_id=data["decision_id"],
            run_id=data["run_id"],
            decision=MatchDecisionType(data["decision"]),
            tier=MatchTier(data["tier"]),
            confidence=float(data["confidence"]),
            reason=data["reason"],
            created_at=data["created_at"],
        )


@dataclass(frozen=True)
class ManualReviewTask:
    review_id: str
    run_id: str
    source_record_id: str
    candidate_ids: list[str]
    status: ReviewStatus
    created_at: str
    updated_at: str
    assigned_to: str | None = None

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)
        _require_utc_z_timestamp("updated_at", self.updated_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "review_id": self.review_id,
            "run_id": self.run_id,
            "source_record_id": self.source_record_id,
            "candidate_ids": self.candidate_ids,
            "status": self.status.value,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "assigned_to": self.assigned_to,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ManualReviewTask":
        return cls(
            review_id=data["review_id"],
            run_id=data["run_id"],
            source_record_id=data["source_record_id"],
            candidate_ids=list(data["candidate_ids"]),
            status=ReviewStatus(data["status"]),
            created_at=data["created_at"],
            updated_at=data["updated_at"],
            assigned_to=data.get("assigned_to"),
        )


@dataclass(frozen=True)
class ManualReviewDecision:
    decision_id: str
    review_id: str
    decision: ReviewDecisionType
    reviewer: str
    notes: str | None
    created_at: str

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "decision_id": self.decision_id,
            "review_id": self.review_id,
            "decision": self.decision.value,
            "reviewer": self.reviewer,
            "notes": self.notes,
            "created_at": self.created_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ManualReviewDecision":
        return cls(
            decision_id=data["decision_id"],
            review_id=data["review_id"],
            decision=ReviewDecisionType(data["decision"]),
            reviewer=data["reviewer"],
            notes=data.get("notes"),
            created_at=data["created_at"],
        )


@dataclass(frozen=True)
class MergeHistoryEvent:
    merge_event_id: str
    event_type: MergeEventType
    winner_golden_record_id: str
    loser_golden_record_id: str
    reason: str
    created_at: str
    run_id: str | None = None

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)

    def to_dict(self) -> dict[str, Any]:
        return {
            "merge_event_id": self.merge_event_id,
            "event_type": self.event_type.value,
            "winner_golden_record_id": self.winner_golden_record_id,
            "loser_golden_record_id": self.loser_golden_record_id,
            "reason": self.reason,
            "created_at": self.created_at,
            "run_id": self.run_id,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MergeHistoryEvent":
        return cls(
            merge_event_id=data["merge_event_id"],
            event_type=MergeEventType(data["event_type"]),
            winner_golden_record_id=data["winner_golden_record_id"],
            loser_golden_record_id=data["loser_golden_record_id"],
            reason=data["reason"],
            created_at=data["created_at"],
            run_id=data.get("run_id"),
        )


@dataclass(frozen=True)
class AuditEvent:
    audit_event_id: str
    event_type: str
    entity_type: str
    entity_id: str
    actor: str
    details: dict[str, Any]
    created_at: str

    def __post_init__(self) -> None:
        _require_utc_z_timestamp("created_at", self.created_at)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AuditEvent":
        return cls(**data)
