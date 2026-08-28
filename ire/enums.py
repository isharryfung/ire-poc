from __future__ import annotations

from enum import StrEnum


class IngestStatus(StrEnum):
    RECEIVED = "RECEIVED"
    VALIDATED = "VALIDATED"
    REJECTED = "REJECTED"


class ValidationStatus(StrEnum):
    VALID = "VALID"
    INVALID = "INVALID"
    WARNING = "WARNING"


class MatchMethod(StrEnum):
    DETERMINISTIC = "DETERMINISTIC"
    PROBABILISTIC = "PROBABILISTIC"
    HYBRID = "HYBRID"


class MatchTier(StrEnum):
    EXACT = "EXACT"
    STRONG = "STRONG"
    WEAK = "WEAK"


class MatchDecisionType(StrEnum):
    AUTO_MERGE = "AUTO_MERGE"
    MANUAL_REVIEW = "MANUAL_REVIEW"
    NON_MATCH = "NON_MATCH"


class ReviewStatus(StrEnum):
    OPEN = "OPEN"
    IN_PROGRESS = "IN_PROGRESS"
    CLOSED = "CLOSED"


class ReviewDecisionType(StrEnum):
    APPROVE_MERGE = "APPROVE_MERGE"
    REJECT_MERGE = "REJECT_MERGE"
    REQUEST_MORE_INFO = "REQUEST_MORE_INFO"


class GoldenRecordStatus(StrEnum):
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    SUPERSEDED = "SUPERSEDED"
    MERGED = "MERGED"


class LinkStatus(StrEnum):
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    REVOKED = "REVOKED"


class MergeEventType(StrEnum):
    AUTO = "AUTO"
    MANUAL = "MANUAL"
    UNMERGE = "UNMERGE"


class SafetyFlag(StrEnum):
    VERIFIED_FIELD_CONFLICT = "VERIFIED_FIELD_CONFLICT"
    LOW_EVIDENCE = "LOW_EVIDENCE"
    HIGH_RISK_ATTRIBUTE = "HIGH_RISK_ATTRIBUTE"


class SourceTrustLevel(StrEnum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
