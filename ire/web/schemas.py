from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class IdentityEnvelope(BaseModel):
    source_system: str = Field(..., min_length=1)
    source_pk: str = Field(..., min_length=1)
    data: dict[str, Any]


class ApproveReviewRequest(BaseModel):
    reviewer: str = Field(..., min_length=1)
    selected_golden_record_id: str = Field(..., min_length=1)
    notes: str | None = None


class RejectReviewRequest(BaseModel):
    reviewer: str = Field(..., min_length=1)
    action: Literal["create-new", "invalid"]
    notes: str | None = None


class PrimaryOverrideRequest(BaseModel):
    value_id: str = Field(..., min_length=1)
    actor: str = Field(..., min_length=1)
    reason: str = Field(..., min_length=1)


class MergePreviewRequest(BaseModel):
    survivor_id: str = Field(..., min_length=1)
    loser_id: str = Field(..., min_length=1)
    proposed_selections: dict[str, str] | None = None


class MergeRequest(BaseModel):
    survivor_id: str = Field(..., min_length=1)
    loser_id: str = Field(..., min_length=1)
    actor: str = Field(..., min_length=1)
    reason: str = Field(..., min_length=1)
    expected_survivor_version: int | None = None
    expected_loser_version: int | None = None
    proposed_selections: dict[str, str] | None = None


class RollbackRequest(BaseModel):
    actor: str = Field(..., min_length=1)
    reason: str = Field(..., min_length=1)


class DuplicateStatusUpdateRequest(BaseModel):
    status: Literal["OPEN", "IN_REVIEW", "CONFIRMED_DUPLICATE", "NOT_DUPLICATE", "DISMISSED"]
    actor: str | None = None
    reason: str | None = None
