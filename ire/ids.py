from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4


def utc_now_iso() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def new_id(prefix: str) -> str:
    return f"{prefix}{uuid4().hex[:12].upper()}"


def new_source_record_id() -> str:
    return new_id("SRC-")


def new_golden_record_id() -> str:
    return new_id("GR-")


def new_record_link_id() -> str:
    return new_id("LINK-")


def new_match_run_id() -> str:
    return new_id("RUN-")


def new_candidate_id() -> str:
    return new_id("CAND-")


def new_review_task_id() -> str:
    return new_id("REV-")


def new_review_decision_id() -> str:
    return new_id("RDEC-")


def new_merge_event_id() -> str:
    return new_id("MERGE-")


def new_audit_event_id() -> str:
    return new_id("AUD-")
