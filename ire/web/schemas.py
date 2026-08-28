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
