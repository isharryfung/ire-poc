from __future__ import annotations

import csv
import hashlib
import io
import json
import re
import shutil
from collections import Counter, defaultdict
from dataclasses import dataclass, replace
from pathlib import Path
from statistics import median
from typing import Any

from .candidate_generation import _name_tokens
from .config import IREConfig
from .enums import (
    DuplicateCandidateStatus,
    DuplicateSeverity,
    GoldenRecordStatus,
    LinkStatus,
)
from .evidence import build_evidence
from .exceptions import NotFoundError, ValidationError
from .golden_merge import DOB_FIELD, STRONG_IDENTIFIER_FIELDS
from .ids import (
    new_duplicate_candidate_id,
    new_duplicate_event_id,
    new_duplicate_scan_run_id,
    utc_now_iso,
)
from .models import (
    DemoScenarioManifest,
    DuplicateCandidate,
    DuplicateScanRun,
    GoldenRecord,
    IntegrityFinding,
    IntegrityReport,
)
from .review import show_review_task
from .safety import mask_value
from .service import process_record
from .validation import validate_record

SCENARIO_VERSION = "1.0.0"
DUPLICATE_SCAN_VERSION = "1.0.0"
HIGH_THRESHOLD = 0.85
MEDIUM_THRESHOLD = 0.6
COMPLETED_DUPLICATE_STATUSES = {
    DuplicateCandidateStatus.CONFIRMED_DUPLICATE,
    DuplicateCandidateStatus.NOT_DUPLICATE,
    DuplicateCandidateStatus.DISMISSED,
    DuplicateCandidateStatus.STALE,
    DuplicateCandidateStatus.MERGED,
}
ANALYST_MUTABLE_STATUSES = {
    DuplicateCandidateStatus.OPEN,
    DuplicateCandidateStatus.IN_REVIEW,
    DuplicateCandidateStatus.CONFIRMED_DUPLICATE,
    DuplicateCandidateStatus.NOT_DUPLICATE,
    DuplicateCandidateStatus.DISMISSED,
}


def _safe_root(root: str | Path) -> Path:
    resolved = Path(root).resolve()
    repo_root = Path(__file__).resolve().parents[1]
    home = Path.home().resolve()
    forbidden = {Path("/").resolve(), repo_root.resolve(), home}
    if resolved in forbidden:
        raise ValidationError(f"refusing dangerous root path: {resolved}")
    if len(resolved.parts) <= 2:
        raise ValidationError(f"root path is too broad: {resolved}")
    return resolved


def _scenario_records(name: str) -> list[dict[str, Any]]:
    baseline = [
        {
            "source_system": "SIS",
            "source_pk": "DEMO-SIS-PETER-001",
            "data": {
                "emplid": "E900001",
                "student_id": "S900001",
                "hkid": "ZA9000011",
                "first_name": "Peter",
                "last_name": "Chan",
                "email": "peter.chan@school.edu.hk",
                "phone": "+852 6123 4567",
                "date_of_birth": "1990-01-02",
                "gender": "M",
                "address": "1 Demo Road, Kowloon",
            },
        },
        {
            "source_system": "SIS",
            "source_pk": "DEMO-SIS-PETER-002",
            "data": {
                "emplid": "E900001",
                "student_id": "S900001",
                "hkid": "ZA9000011",
                "first_name": "Peter",
                "last_name": "Chan",
                "email": "pchan@school.edu.hk",
                "phone": "61234567",
                "date_of_birth": "1990-01-02",
                "gender": "M",
                "address": "1 Demo Road Kowloon",
            },
        },
        {
            "source_system": "PORTAL",
            "source_pk": "DEMO-PORTAL-PETER-001",
            "data": {
                "first_name": "Peter",
                "last_name": "Chan",
                "email": "peter.chan@school.edu.hk",
                "phone": "61234567",
                "date_of_birth": "1990-01-02",
                "gender": "M",
                "address": "1 Demo Road, Kowloon",
            },
        },
        {
            "source_system": "SIS",
            "source_pk": "DEMO-SIS-BELLA-001",
            "data": {
                "emplid": "E900002",
                "student_id": "S900002",
                "hkid": "ZB9000022",
                "first_name": "Bella",
                "last_name": "Tam",
                "email": "bella.tam@school.edu.hk",
                "phone": "+852 5666 7777",
                "date_of_birth": "1995-08-09",
                "gender": "F",
                "address": "5 Queen Road Central",
            },
        },
        {
            "source_system": "ALUMNI",
            "source_pk": "DEMO-ALUMNI-BELLA-001",
            "data": {
                "alumni_id": "AL900002",
                "first_name": "Bella",
                "last_name": "Tam",
                "email": "bella.tam@school.edu.hk",
                "phone": "56667777",
                "date_of_birth": "1995-08-09",
                "gender": "F",
                "address": "5 Queen Road Central",
            },
        },
        {
            "source_system": "SIS",
            "source_pk": "DEMO-SIS-PETER-CONFLICT",
            "data": {
                "emplid": "E900001",
                "student_id": "S900001",
                "hkid": "ZZ9999999",
                "first_name": "Peter",
                "last_name": "Chan",
                "email": "peter.chan@school.edu.hk",
                "phone": "61234567",
                "date_of_birth": "1990-01-02",
                "gender": "M",
                "address": "1 Demo Road, Kowloon",
            },
        },
        {
            "source_system": "UNKNOWN_SYSTEM",
            "source_pk": "DEMO-INVALID-001",
            "data": {"first_name": "Invalid", "last_name": "Record"},
        },
    ]
    if name == "empty":
        return []
    if name == "standard":
        return baseline[:3]
    if name == "matching":
        return baseline[:3] + [baseline[6]]
    if name == "conflict":
        return baseline[:2] + [baseline[5]]
    if name == "golden-merge":
        return baseline[:5]
    if name == "rollback":
        return baseline[:5]
    if name == "full-showcase":
        return baseline
    raise ValidationError(f"unsupported scenario: {name}")


def demo_reset(repo, root: str | Path, *, yes: bool = False) -> dict[str, Any]:
    if not yes:
        raise ValidationError("demo reset requires explicit --yes confirmation")
    safe_root = _safe_root(root)
    if safe_root.exists():
        shutil.rmtree(safe_root)
    repo.__class__(safe_root).initialize_storage()
    return {"root": str(safe_root), "reset": True}


def _seed_extra_actions(repo, config: IREConfig, scenario: str) -> None:
    if scenario not in {"golden-merge", "rollback", "full-showcase"}:
        return
    goldens = [item for item in repo.load_golden_records() if item.status == GoldenRecordStatus.ACTIVE]
    if len(goldens) < 2:
        return
    left, right = goldens[0], goldens[1]
    from .golden_merge import merge_golden_records

    try:
        merge_golden_records(left.golden_record_id, right.golden_record_id, "demo-seeder", "demo merge", repo)
    except Exception:
        return


def demo_seed(repo, config: IREConfig, scenario: str, *, force: bool = False) -> dict[str, Any]:
    existing = repo.load_demo_manifest()
    if existing and existing.scenario == scenario and existing.version == SCENARIO_VERSION and not force:
        return {"seeded": False, "reason": "scenario already seeded", "manifest": existing.to_dict()}
    if existing and not force:
        raise ValidationError("a different scenario is already seeded; reset first or use --force")

    records = _scenario_records(scenario)
    success_count = 0
    if force:
        root = _safe_root(repo.root)
        shutil.rmtree(root, ignore_errors=True)
        repo = repo.__class__(root)
        repo.initialize_storage()
    for payload in records:
        try:
            process_record(payload, config, repo)
            success_count += 1
        except Exception:
            continue
    _seed_extra_actions(repo, config, scenario)
    status = demo_status(repo)
    manifest = DemoScenarioManifest(
        scenario=scenario,
        version=SCENARIO_VERSION,
        seeded_at=utc_now_iso(),
        seed_count=success_count,
        notable_ids=status["notable_ids"],
    )
    repo.save_demo_manifest(manifest)
    return {"seeded": True, "manifest": manifest.to_dict(), "status": status}


def demo_status(repo) -> dict[str, Any]:
    manifest = repo.load_demo_manifest()
    goldens = repo.load_golden_records()
    links = repo.load_record_links()
    reviews = repo.load_manual_review_tasks()
    duplicates = refresh_duplicate_candidate_statuses(repo)
    return {
        "manifest": manifest.to_dict() if manifest else None,
        "counts": {
            "golden_records": len(goldens),
            "active_links": sum(1 for link in links if link.status == LinkStatus.ACTIVE),
            "open_reviews": sum(1 for item in reviews if item.status.value == "OPEN"),
            "duplicate_candidates": len(duplicates),
        },
        "storage_health": "OK" if not integrity_check(repo).findings else "WARN",
        "notable_ids": {
            "latest_golden": goldens[-1].golden_record_id if goldens else "",
            "latest_review": reviews[-1].review_id if reviews else "",
            "latest_duplicate": duplicates[-1].candidate_id if duplicates else "",
        },
    }


def _primary_value(record: GoldenRecord, field_name: str) -> str | None:
    values = record.fields.get(field_name, [])
    for value in values:
        if value.is_primary and value.is_active:
            return value.normalized_value or value.raw_value
    return None


def _active_values(record: GoldenRecord, field_name: str) -> set[str]:
    return {
        (value.normalized_value or value.raw_value)
        for value in record.fields.get(field_name, [])
        if value.is_active and (value.normalized_value or value.raw_value)
    }


def _golden_as_normalized(record: GoldenRecord) -> dict[str, Any]:
    normalized: dict[str, Any] = {}
    for field in (
        "hkid",
        "emplid",
        "student_id",
        "alumni_id",
        "email",
        "phone",
        "date_of_birth",
        "gender",
        "first_name",
        "last_name",
        "full_name",
        "address",
    ):
        normalized[field] = _primary_value(record, field)
    phone = normalized.get("phone")
    if phone:
        digits = "".join(ch for ch in str(phone) if ch.isdigit())
        normalized["phone"] = digits
        normalized["phone_digits"] = digits
        normalized["phone_last8"] = digits[-8:]
    normalized["address_tokens"] = str(normalized.get("address") or "").split()
    if not normalized.get("full_name"):
        normalized["full_name"] = " ".join(
            [value for value in [normalized.get("first_name"), normalized.get("last_name")] if value]
        )
    return normalized


def _blocking_keys(record: GoldenRecord) -> set[str]:
    keys: set[str] = set()
    normalized = _golden_as_normalized(record)
    for field in ("hkid", "emplid", "student_id", "alumni_id", "email"):
        value = normalized.get(field)
        if value:
            keys.add(f"{field}:{value}")
    phone_digits = normalized.get("phone_digits")
    if phone_digits:
        keys.add(f"phone:{phone_digits}")
        keys.add(f"phone8:{phone_digits[-8:]}")
    dob = normalized.get("date_of_birth")
    last = normalized.get("last_name")
    if dob and last:
        keys.add(f"namedob:{last}:{dob}")
    tokens = _name_tokens(record)
    for token in tokens:
        if dob:
            keys.add(f"tokendob:{token}:{dob}")
    return keys


def _pair_key(left_id: str, right_id: str) -> tuple[str, str, str]:
    ordered = sorted([left_id, right_id])
    return ordered[0], ordered[1], f"{ordered[0]}::{ordered[1]}"


def _severity(score: float, blocked: bool) -> DuplicateSeverity:
    if blocked:
        return DuplicateSeverity.BLOCKED
    if score >= HIGH_THRESHOLD:
        return DuplicateSeverity.HIGH
    return DuplicateSeverity.MEDIUM


def _fingerprint(left: GoldenRecord, right: GoldenRecord, policy_version: str) -> str:
    payload = {
        "left": left.golden_record_id,
        "right": right.golden_record_id,
        "left_version": left.version,
        "right_version": right.version,
        "policy_version": policy_version,
    }
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True).encode("utf-8")).hexdigest()
    return digest[:20]


def refresh_duplicate_candidate_statuses(repo) -> list[DuplicateCandidate]:
    candidates = repo.load_duplicate_candidates()
    if not candidates:
        return []
    by_id = {g.golden_record_id: g for g in repo.load_golden_records()}
    changed = False
    refreshed: list[DuplicateCandidate] = []
    for candidate in candidates:
        current = candidate
        if candidate.status in COMPLETED_DUPLICATE_STATUSES:
            refreshed.append(current)
            continue
        left = by_id.get(candidate.left_golden_record_id)
        right = by_id.get(candidate.right_golden_record_id)
        if left is None or right is None:
            current = replace(
                current,
                status=DuplicateCandidateStatus.STALE,
                actor="system",
                reason="golden record missing",
                updated_at=utc_now_iso(),
            )
        elif left.superseded_by == right.golden_record_id or right.superseded_by == left.golden_record_id:
            current = replace(
                current,
                status=DuplicateCandidateStatus.MERGED,
                actor="system",
                reason="records merged",
                updated_at=utc_now_iso(),
            )
        elif left.version != candidate.left_version or right.version != candidate.right_version:
            current = replace(
                current,
                status=DuplicateCandidateStatus.STALE,
                actor="system",
                reason="record version changed",
                updated_at=utc_now_iso(),
            )
        if current != candidate:
            changed = True
            repo.append_duplicate_candidate_event(
                {
                    "event_id": new_duplicate_event_id(),
                    "candidate_id": current.candidate_id,
                    "event_type": "STATUS_CHANGED",
                    "actor": current.actor or "system",
                    "reason": current.reason,
                    "status": current.status.value,
                    "created_at": current.updated_at,
                }
            )
        refreshed.append(current)
    if changed:
        repo.save_duplicate_candidates(refreshed)
    return refreshed


def duplicate_scan(repo, config: IREConfig, *, include_superseded: bool = False) -> dict[str, Any]:
    existing = refresh_duplicate_candidate_statuses(repo)
    existing_by_pair = defaultdict(list)
    for item in existing:
        existing_by_pair[item.pair_key].append(item)

    records = repo.load_golden_records()
    active = [
        item
        for item in records
        if include_superseded or (item.status == GoldenRecordStatus.ACTIVE and item.superseded_by is None)
    ]
    index: dict[str, set[str]] = defaultdict(set)
    by_id = {item.golden_record_id: item for item in active}
    for record in active:
        for key in _blocking_keys(record):
            index[key].add(record.golden_record_id)

    candidate_pairs: set[tuple[str, str, str]] = set()
    for ids in index.values():
        sorted_ids = sorted(ids)
        for i in range(len(sorted_ids)):
            for j in range(i + 1, len(sorted_ids)):
                candidate_pairs.add(_pair_key(sorted_ids[i], sorted_ids[j]))
    if not candidate_pairs and len(active) <= config.matching_policy.full_scan_limit:
        for i in range(len(active)):
            for j in range(i + 1, len(active)):
                candidate_pairs.add(_pair_key(active[i].golden_record_id, active[j].golden_record_id))

    now = utc_now_iso()
    run_id = new_duplicate_scan_run_id()
    updated = list(existing)
    scan_count = 0
    for left_id, right_id, pair_key in sorted(candidate_pairs):
        left = by_id.get(left_id)
        right = by_id.get(right_id)
        if left is None or right is None:
            continue
        normalized = _golden_as_normalized(left)
        evidence = build_evidence(normalized, right.fields, config)
        comparable = [item for item in evidence if item.is_comparable]
        if not comparable:
            continue
        score = round(sum(item.weighted_score for item in comparable), 6)
        strong_conflicts = [
            field
            for field in STRONG_IDENTIFIER_FIELDS
            if _active_values(left, field) and _active_values(right, field) and _active_values(left, field).isdisjoint(_active_values(right, field))
        ]
        dob_conflict = bool(
            _active_values(left, DOB_FIELD)
            and _active_values(right, DOB_FIELD)
            and _active_values(left, DOB_FIELD).isdisjoint(_active_values(right, DOB_FIELD))
        )
        blocked = bool(strong_conflicts or dob_conflict)
        severity = _severity(score, blocked)
        fingerprint = _fingerprint(left, right, config.matching_policy.version)
        matching_blocks = sorted(_blocking_keys(left) & _blocking_keys(right))
        if not matching_blocks and len(active) <= config.matching_policy.full_scan_limit:
            matching_blocks = ["full_scan_fallback"]
        open_existing = next(
            (
                item
                for item in existing_by_pair.get(pair_key, [])
                if item.fingerprint == fingerprint and item.status not in COMPLETED_DUPLICATE_STATUSES
            ),
            None,
        )
        closed_same_fingerprint = any(
            item.fingerprint == fingerprint and item.status in COMPLETED_DUPLICATE_STATUSES
            for item in existing_by_pair.get(pair_key, [])
        )
        if closed_same_fingerprint and open_existing is None:
            continue
        if open_existing is not None:
            replacement = replace(
                open_existing,
                severity=severity,
                score=score,
                comparable_fields=len(comparable),
                blocking_reasons=(["STRONG_IDENTIFIER_CONFLICT"] if strong_conflicts else [])
                + (["DATE_OF_BIRTH_CONFLICT"] if dob_conflict else []),
                strong_identifier_conflicts=strong_conflicts,
                dob_conflict=dob_conflict,
                evidence=[item.__dict__ for item in comparable],
                blocking_matches=matching_blocks,
                latest_scan_run_id=run_id,
                updated_at=now,
            )
            updated = [replacement if item.candidate_id == replacement.candidate_id else item for item in updated]
            repo.append_duplicate_candidate_event(
                {
                    "event_id": new_duplicate_event_id(),
                    "candidate_id": replacement.candidate_id,
                    "event_type": "REFRESHED",
                    "actor": "system",
                    "status": replacement.status.value,
                    "created_at": now,
                }
            )
            scan_count += 1
            continue

        created = DuplicateCandidate(
            candidate_id=new_duplicate_candidate_id(),
            pair_key=pair_key,
            left_golden_record_id=left_id,
            right_golden_record_id=right_id,
            left_version=left.version,
            right_version=right.version,
            severity=severity,
            status=DuplicateCandidateStatus.OPEN if not blocked else DuplicateCandidateStatus.OPEN,
            score=score,
            comparable_fields=len(comparable),
            fingerprint=fingerprint,
            blocking_reasons=(["STRONG_IDENTIFIER_CONFLICT"] if strong_conflicts else [])
            + (["DATE_OF_BIRTH_CONFLICT"] if dob_conflict else []),
            strong_identifier_conflicts=strong_conflicts,
            dob_conflict=dob_conflict,
            evidence=[item.__dict__ for item in comparable],
            blocking_matches=matching_blocks,
            latest_scan_run_id=run_id,
            created_at=now,
            updated_at=now,
        )
        updated.append(created)
        repo.append_duplicate_candidate_event(
            {
                "event_id": new_duplicate_event_id(),
                "candidate_id": created.candidate_id,
                "event_type": "CREATED",
                "actor": "system",
                "status": created.status.value,
                "created_at": now,
            }
        )
        scan_count += 1

    repo.save_duplicate_candidates(updated)
    run = DuplicateScanRun(
        scan_run_id=run_id,
        policy_version=config.matching_policy.version,
        scan_version=DUPLICATE_SCAN_VERSION,
        created_at=now,
        candidate_count=scan_count,
        notes={"total_pairs": len(candidate_pairs), "active_records": len(active)},
    )
    repo.append_duplicate_scan_run(run)
    return {
        "scan_run": run.to_dict(),
        "candidate_count": scan_count,
    }


def list_duplicate_candidates(
    repo,
    *,
    status: str | None = None,
    severity: str | None = None,
    min_score: float | None = None,
    max_score: float | None = None,
    has_conflict: bool | None = None,
    golden_id: str | None = None,
    scan_run_id: str | None = None,
    search: str | None = None,
) -> list[DuplicateCandidate]:
    rows = refresh_duplicate_candidate_statuses(repo)
    if status:
        rows = [item for item in rows if item.status.value == status.upper()]
    if severity:
        rows = [item for item in rows if item.severity.value == severity.upper()]
    if min_score is not None:
        rows = [item for item in rows if item.score >= min_score]
    if max_score is not None:
        rows = [item for item in rows if item.score <= max_score]
    if has_conflict is not None:
        rows = [item for item in rows if bool(item.strong_identifier_conflicts or item.dob_conflict) == has_conflict]
    if golden_id:
        rows = [item for item in rows if golden_id in (item.left_golden_record_id, item.right_golden_record_id)]
    if scan_run_id:
        rows = [item for item in rows if item.latest_scan_run_id == scan_run_id]
    if search:
        needle = search.lower()
        rows = [
            item
            for item in rows
            if needle in item.candidate_id.lower()
            or needle in item.pair_key.lower()
            or needle in item.left_golden_record_id.lower()
            or needle in item.right_golden_record_id.lower()
        ]
    return sorted(rows, key=lambda item: item.updated_at, reverse=True)


def show_duplicate_candidate(repo, candidate_id: str) -> DuplicateCandidate:
    rows = refresh_duplicate_candidate_statuses(repo)
    candidate = next((item for item in rows if item.candidate_id == candidate_id), None)
    if candidate is None:
        raise NotFoundError(f"duplicate candidate not found: {candidate_id}")
    return candidate


def update_duplicate_candidate_status(
    repo,
    candidate_id: str,
    status: str,
    *,
    actor: str | None,
    reason: str | None,
) -> DuplicateCandidate:
    try:
        target_status = DuplicateCandidateStatus(status.upper())
    except ValueError as exc:
        raise ValidationError(f"invalid duplicate candidate status: {status}") from exc
    if target_status not in ANALYST_MUTABLE_STATUSES:
        raise ValidationError("status transition is not allowed for analyst updates")
    rows = refresh_duplicate_candidate_statuses(repo)
    candidate = next((item for item in rows if item.candidate_id == candidate_id), None)
    if candidate is None:
        raise NotFoundError(f"duplicate candidate not found: {candidate_id}")
    if candidate.status in COMPLETED_DUPLICATE_STATUSES:
        raise ValidationError("candidate is already completed")
    if target_status in {DuplicateCandidateStatus.NOT_DUPLICATE, DuplicateCandidateStatus.DISMISSED} and (
        not actor or not reason
    ):
        raise ValidationError("actor and reason are required for this status transition")
    now = utc_now_iso()
    updated = replace(
        candidate,
        status=target_status,
        actor=actor or "system",
        reason=reason,
        updated_at=now,
    )
    repo.save_duplicate_candidates([updated if item.candidate_id == candidate_id else item for item in rows])
    repo.append_duplicate_candidate_event(
        {
            "event_id": new_duplicate_event_id(),
            "candidate_id": candidate_id,
            "event_type": "STATUS_CHANGED",
            "actor": actor or "system",
            "reason": reason,
            "status": target_status.value,
            "created_at": now,
        }
    )
    return updated


def _finding(code: str, severity: str, entity: str, description: str, repair: str, auto: bool = False) -> IntegrityFinding:
    return IntegrityFinding(
        code=code,
        severity=severity,
        entity=entity,
        description=description,
        repair_category=repair,
        auto_repairable=auto,
    )


def integrity_check(repo) -> IntegrityReport:
    findings: list[IntegrityFinding] = []
    # parsing checks
    for path in (
        repo.golden_records_path,
        repo.record_links_path,
        repo.review_tasks_path,
        repo.duplicate_candidates_path,
    ):
        try:
            repo._read_state_array(path)
        except Exception:
            findings.append(_finding("STATE_PARSE_ERROR", "CRITICAL", str(path.name), "state JSON cannot be parsed", "restore_backup"))
    for path in (
        repo.source_records_path,
        repo.match_runs_path,
        repo.merge_history_path,
        repo.audit_log_path,
        repo.merge_events_path,
        repo.primary_overrides_path,
        repo.merge_rollbacks_path,
        repo.duplicate_scan_runs_path,
        repo.duplicate_candidate_events_path,
    ):
        try:
            repo._load_typed_jsonl(path, lambda item: item)
        except Exception:
            findings.append(_finding("EVENT_PARSE_ERROR", "CRITICAL", str(path.name), "event JSONL contains invalid row", "repair_line"))

    goldens = repo.load_golden_records()
    links = repo.load_record_links()
    sources = repo.load_source_records()
    runs = repo.load_match_runs()
    reviews = repo.load_manual_review_tasks()
    merge_events = repo.load_merge_events()
    rollbacks = repo.load_rollback_events()
    duplicates = repo.load_duplicate_candidates()
    scan_runs = repo.load_duplicate_scan_runs()

    if len({g.golden_record_id for g in goldens}) != len(goldens):
        findings.append(_finding("DUPLICATE_GOLDEN_ID", "ERROR", "golden_records", "duplicate golden_record_id found", "deduplicate"))
    if len({l.link_id for l in links}) != len(links):
        findings.append(_finding("DUPLICATE_LINK_ID", "ERROR", "record_links", "duplicate link_id found", "deduplicate"))
    if len({s.source_record_id for s in sources}) != len(sources):
        findings.append(_finding("DUPLICATE_SOURCE_ID", "ERROR", "source_records", "duplicate source_record_id found", "deduplicate"))
    if len({r.run_id for r in runs}) != len(runs):
        findings.append(_finding("DUPLICATE_RUN_ID", "ERROR", "match_runs", "duplicate run_id found", "deduplicate"))
    if len({d.candidate_id for d in duplicates}) != len(duplicates):
        findings.append(_finding("DUPLICATE_DUPLICATE_ID", "ERROR", "duplicate_candidates", "duplicate candidate_id found", "deduplicate"))

    golden_ids = {g.golden_record_id for g in goldens}
    source_ids = {s.source_record_id for s in sources}
    run_ids = {r.run_id for r in runs}
    merge_ids = {m.merge_id for m in merge_events}
    rollback_merge_ids = [item.merge_id for item in rollbacks]
    scan_run_ids = {item.scan_run_id for item in scan_runs}

    for golden in goldens:
        for field_name, values in golden.fields.items():
            if not values:
                continue
            active_primaries = [value for value in values if value.is_active and value.is_primary]
            if len(active_primaries) == 0:
                findings.append(_finding("MISSING_PRIMARY", "ERROR", golden.golden_record_id, f"{field_name} has no active primary", "set_primary", True))
            if len(active_primaries) > 1:
                findings.append(_finding("MULTIPLE_PRIMARIES", "ERROR", golden.golden_record_id, f"{field_name} has multiple active primaries", "keep_best_primary", True))
            if any(value.is_primary and not value.is_active for value in values):
                findings.append(_finding("INACTIVE_PRIMARY", "ERROR", golden.golden_record_id, f"{field_name} has inactive primary value", "activate_or_reassign"))
            value_ids = [value.value_id for value in values]
            if len(value_ids) != len(set(value_ids)):
                findings.append(_finding("DUPLICATE_VALUE_ID", "WARNING", golden.golden_record_id, f"{field_name} has duplicate value_id", "deduplicate_values"))
        if golden.status == GoldenRecordStatus.SUPERSEDED:
            if not golden.superseded_by or golden.superseded_by not in golden_ids:
                findings.append(_finding("INVALID_SUPERSEDED_TARGET", "ERROR", golden.golden_record_id, "superseded record has invalid superseded_by", "repair_reference"))

    for link in links:
        if link.golden_record_id not in golden_ids:
            findings.append(_finding("ORPHAN_LINK_GOLDEN", "ERROR", link.link_id, "record link points to missing golden record", "repair_reference"))
        if link.source_record_id not in source_ids:
            findings.append(_finding("ORPHAN_LINK_SOURCE", "ERROR", link.link_id, "record link points to missing source record", "repair_reference"))
    active_by_source = Counter(link.source_record_id for link in links if link.status == LinkStatus.ACTIVE)
    for source_id, count in active_by_source.items():
        if count > 1:
            findings.append(_finding("DUPLICATE_ACTIVE_LINK", "ERROR", source_id, "source record has multiple active links", "deactivate_extra_links"))

    for golden in goldens:
        seen: set[str] = set()
        cursor = golden
        while cursor.superseded_by:
            if cursor.golden_record_id in seen:
                findings.append(_finding("SUPERSEDED_CYCLE", "CRITICAL", golden.golden_record_id, "superseded_by chain has cycle", "break_cycle"))
                break
            seen.add(cursor.golden_record_id)
            next_record = next((item for item in goldens if item.golden_record_id == cursor.superseded_by), None)
            if next_record is None:
                break
            cursor = next_record

    for event in merge_events:
        if event.survivor_id not in golden_ids and not event.survivor_before:
            findings.append(_finding("MISSING_MERGE_SURVIVOR", "ERROR", event.merge_id, "merge event survivor does not exist", "repair_reference"))
        if event.loser_id not in golden_ids and not event.loser_before:
            findings.append(_finding("MISSING_MERGE_LOSER", "ERROR", event.merge_id, "merge event loser does not exist", "repair_reference"))
    if len(rollback_merge_ids) != len(set(rollback_merge_ids)):
        findings.append(_finding("DUPLICATE_ROLLBACK", "ERROR", "merge_rollbacks", "multiple rollbacks for same merge", "deduplicate"))
    for rollback in rollbacks:
        if rollback.merge_id not in merge_ids:
            findings.append(_finding("ROLLBACK_MISSING_MERGE", "ERROR", rollback.rollback_id, "rollback references missing merge", "repair_reference"))

    for task in reviews:
        if task.source_record_id not in source_ids:
            findings.append(_finding("REVIEW_MISSING_SOURCE", "ERROR", task.review_id, "review references missing source", "repair_reference"))
        if task.run_id not in run_ids:
            findings.append(_finding("REVIEW_MISSING_RUN", "ERROR", task.review_id, "review references missing run", "repair_reference"))

    for candidate in duplicates:
        if candidate.left_golden_record_id not in golden_ids or candidate.right_golden_record_id not in golden_ids:
            findings.append(_finding("DUPLICATE_MISSING_GOLDEN", "ERROR", candidate.candidate_id, "duplicate candidate references missing golden record", "repair_reference"))
        if candidate.latest_scan_run_id and candidate.latest_scan_run_id not in scan_run_ids:
            findings.append(_finding("DUPLICATE_MISSING_SCAN_RUN", "WARNING", candidate.candidate_id, "duplicate candidate references missing scan run", "repair_reference"))

    summary = Counter(item.severity for item in findings)
    return IntegrityReport(generated_at=utc_now_iso(), findings=findings, summary=dict(summary))


def integrity_repair_preview(repo) -> dict[str, Any]:
    report = integrity_check(repo)
    proposals = []
    for finding in report.findings:
        if finding.code == "MISSING_PRIMARY":
            proposals.append(
                {
                    "finding_code": finding.code,
                    "entity": finding.entity,
                    "action": "Select highest-trust active value as primary",
                    "deterministic": True,
                    "destructive": False,
                }
            )
        elif finding.code == "MULTIPLE_PRIMARIES":
            proposals.append(
                {
                    "finding_code": finding.code,
                    "entity": finding.entity,
                    "action": "Keep one primary and clear others",
                    "deterministic": True,
                    "destructive": False,
                }
            )
    return {
        "generated_at": utc_now_iso(),
        "preview_only": True,
        "findings_summary": report.summary,
        "proposals": proposals,
    }


def data_quality_summary(repo, config: IREConfig, *, source_system: str | None = None) -> dict[str, Any]:
    duplicates = refresh_duplicate_candidate_statuses(repo)
    report = integrity_check(repo)
    source_records = repo.load_source_records()
    runs = repo.load_match_runs()
    audits = repo.load_audit_events()
    all_source_records = list(source_records)
    by_source_record = {item.source_record_id: item for item in all_source_records}
    if source_system:
        source_system = source_system.upper()
        source_records = [item for item in source_records if item.source_system.upper() == source_system]
        selected = {item.source_record_id for item in source_records}
        runs = [item for item in runs if item.source_record_id in selected]

    total = len(source_records)
    validations = 0
    invalid_email = 0
    invalid_phone = 0
    missing_strong = 0
    missing_dob = 0
    for record in source_records:
        payload = {"source_system": record.source_system, "source_pk": record.source_pk, "data": record.payload}
        result = validate_record(payload, config)
        if not result.valid:
            validations += 1
        if any(issue.field == "email" and issue.code == "INVALID_FORMAT" for issue in result.issues):
            invalid_email += 1
        if any(issue.field == "phone" and issue.code == "INVALID_FORMAT" for issue in result.issues):
            invalid_phone += 1
        if not any(result.normalized.get(key) for key in ("hkid", "emplid", "student_id", "alumni_id")):
            missing_strong += 1
        if not result.normalized.get("date_of_birth"):
            missing_dob += 1

    best_scores = []
    for run in runs:
        best = next((item for item in run.candidates if item.candidate_id == run.best_candidate_id), run.candidates[0] if run.candidates else None)
        if best is not None:
            best_scores.append(best.score)

    total_runs = len(runs)
    decision_counts = Counter(run.decision.value for run in runs)
    review_safety_flags = Counter()
    for run in runs:
        best = next((item for item in run.candidates if item.candidate_id == run.best_candidate_id), run.candidates[0] if run.candidates else None)
        if best is not None:
            for flag in best.safety_flags:
                review_safety_flags[flag.value if hasattr(flag, "value") else str(flag)] += 1

    duplicate_submissions = sum(
        1
        for item in audits
        if item.event_type == "DUPLICATE"
        and (
            source_system is None
            or str(item.details.get("source_system") or "").upper() == source_system
        )
    )
    source_revisions = sum(1 for item in source_records if item.supersedes_source_record_id is not None)
    source_ids_by_golden: dict[str, set[str]] = defaultdict(set)
    source_by_id = {item.source_record_id: item for item in all_source_records}
    for link in repo.load_record_links():
        source_record = source_by_id.get(link.source_record_id)
        if source_record is None:
            continue
        source_ids_by_golden[link.golden_record_id].add(source_record.source_system.upper())
    open_duplicates = [
        item
        for item in duplicates
        if item.status in {DuplicateCandidateStatus.OPEN, DuplicateCandidateStatus.IN_REVIEW}
        and (
            source_system is None
            or source_system in source_ids_by_golden.get(item.left_golden_record_id, set())
            or source_system in source_ids_by_golden.get(item.right_golden_record_id, set())
        )
    ]
    open_by_severity = Counter(item.severity.value for item in open_duplicates)

    def rate(value: int, denom: int) -> float:
        return round((value / denom), 6) if denom else 0.0

    overall = {
        "records_processed": total,
        "validation_failure_count": validations,
        "validation_failure_rate": rate(validations, total),
        "missing_strong_identifier_count": missing_strong,
        "missing_strong_identifier_rate": rate(missing_strong, total),
        "missing_dob_count": missing_dob,
        "missing_dob_rate": rate(missing_dob, total),
        "invalid_email_count": invalid_email,
        "invalid_email_rate": rate(invalid_email, total),
        "invalid_phone_count": invalid_phone,
        "invalid_phone_rate": rate(invalid_phone, total),
        "duplicate_submission_count": duplicate_submissions,
        "duplicate_submission_rate": rate(duplicate_submissions, total),
        "source_revision_count": source_revisions,
        "source_revision_rate": rate(source_revisions, total),
        "auto_merge_count": decision_counts.get("AUTO_MERGE", 0),
        "auto_merge_rate": rate(decision_counts.get("AUTO_MERGE", 0), total_runs),
        "manual_review_count": decision_counts.get("MANUAL_REVIEW", 0),
        "manual_review_rate": rate(decision_counts.get("MANUAL_REVIEW", 0), total_runs),
        "create_new_count": decision_counts.get("CREATE_NEW_GOLDEN", 0),
        "create_new_rate": rate(decision_counts.get("CREATE_NEW_GOLDEN", 0), total_runs),
        "strong_identifier_conflict_count": review_safety_flags.get("TIER1_IDENTIFIER_CONFLICT", 0),
        "dob_conflict_count": review_safety_flags.get("DATE_OF_BIRTH_CONFLICT", 0),
        "candidate_ambiguity_count": review_safety_flags.get("MULTIPLE_HIGH_CONFIDENCE_CANDIDATES", 0)
        + review_safety_flags.get("LOW_TOP_CANDIDATE_GAP", 0),
        "average_final_confidence": round(sum(best_scores) / len(best_scores), 6) if best_scores else 0.0,
        "median_final_confidence": round(median(best_scores), 6) if best_scores else 0.0,
        "open_duplicate_candidate_count_by_severity": dict(open_by_severity),
        "integrity_finding_count_by_severity": dict(report.summary),
    }

    sources: dict[str, dict[str, Any]] = {}
    for record in source_records:
        row = sources.setdefault(record.source_system, {"records_processed": 0, "revisions": 0, "runs": 0, "validation_failures": 0})
        row["records_processed"] += 1
        row["revisions"] += 1 if record.supersedes_source_record_id else 0
    for run in runs:
        source = by_source_record.get(run.source_record_id)
        if source is None:
            continue
        row = sources.setdefault(source.source_system, {"records_processed": 0, "revisions": 0, "runs": 0, "validation_failures": 0})
        row["runs"] += 1
    for audit in audits:
        if audit.event_type != "VALIDATION_FAILED":
            continue
        src = str(audit.details.get("source_system") or "")
        if source_system is not None and src.upper() != source_system:
            continue
        row = sources.setdefault(src, {"records_processed": 0, "revisions": 0, "runs": 0, "validation_failures": 0})
        row["validation_failures"] += 1

    return {
        "generated_at": utc_now_iso(),
        "source_filter": source_system,
        "denominators": {"records": total, "match_runs": total_runs},
        "overall": overall,
        "sources": sources,
    }


def _csv_safe_cell(value: Any) -> str:
    text = "" if value is None else str(value)
    if text.startswith(("=", "+", "-", "@")):
        return "'" + text
    return text


def _flatten(obj: Any) -> Any:
    if isinstance(obj, (dict, list)):
        return json.dumps(obj, ensure_ascii=False, sort_keys=True)
    return obj


def _dataset_rows(repo, config: IREConfig, dataset: str, filters: dict[str, Any] | None = None) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    filters = filters or {}
    if dataset == "golden-records":
        rows = []
        for golden in repo.load_golden_records():
            primary = {}
            for field_name, values in golden.fields.items():
                current = next((value for value in values if value.is_primary and value.is_active), values[0] if values else None)
                primary[field_name] = mask_value(field_name, (current.normalized_value or current.raw_value) if current else None)
            rows.append(
                {
                    "golden_record_id": golden.golden_record_id,
                    "status": golden.status.value,
                    "version": golden.version,
                    "updated_at": golden.updated_at,
                    "primary_values": primary,
                }
            )
        return rows, {"masked": True}
    if dataset == "record-links":
        return [item.to_dict() for item in repo.load_record_links()], {"masked": True}
    if dataset == "review-queue":
        rows = []
        for task in repo.load_manual_review_tasks():
            detail = show_review_task(task.review_id, repo)
            rows.append(
                {
                    "review_id": task.review_id,
                    "status": task.status.value,
                    "source_record_id": task.source_record_id,
                    "source_system": detail.source_record.source_system,
                    "safety_flags": task.safety_flags,
                    "created_at": task.created_at,
                }
            )
        return rows, {"masked": True}
    if dataset == "duplicate-candidates":
        return [item.to_dict() for item in list_duplicate_candidates(repo)], {"masked": True}
    if dataset == "match-history":
        rows = []
        for run in repo.load_match_runs():
            best = next((item for item in run.candidates if item.candidate_id == run.best_candidate_id), run.candidates[0] if run.candidates else None)
            rows.append(
                {
                    "run_id": run.run_id,
                    "decision": run.decision.value,
                    "best_candidate": best.golden_record_id if best else None,
                    "score": best.score if best else 0.0,
                    "created_at": run.created_at,
                }
            )
        return rows, {"masked": True}
    if dataset == "activity-log":
        return [item.to_dict() for item in repo.load_audit_events()], {"masked": True}
    if dataset == "data-quality":
        return [data_quality_summary(repo, config)], {"masked": True}
    if dataset == "integrity-findings":
        report = integrity_check(repo)
        return [item.to_dict() for item in report.findings], {"masked": True, "generated_at": report.generated_at}
    if dataset == "golden-record-report":
        golden_id = str(filters.get("golden_id") or "")
        if not golden_id:
            raise ValidationError("golden_id is required for golden-record-report export")
        golden = repo.find_golden_record(golden_id)
        if golden is None:
            raise NotFoundError(f"golden record not found: {golden_id}")
        row = {
            "golden_record_id": golden.golden_record_id,
            "status": golden.status.value,
            "version": golden.version,
            "fields": {
                field_name: [mask_value(field_name, item.normalized_value or item.raw_value) for item in values]
                for field_name, values in golden.fields.items()
            },
            "updated_at": golden.updated_at,
        }
        return [row], {"masked": True}
    raise ValidationError(f"unsupported export dataset: {dataset}")


def export_dataset(
    repo,
    config: IREConfig,
    dataset: str,
    format_name: str,
    *,
    filters: dict[str, Any] | None = None,
) -> tuple[str, bytes]:
    rows, metadata = _dataset_rows(repo, config, dataset, filters)
    generated_at = utc_now_iso()
    payload = {
        "schema_version": "phase1.3",
        "dataset": dataset,
        "generated_at": generated_at,
        "filters": filters or {},
        "masked": True,
        "metadata": metadata,
        "rows": rows,
    }
    if format_name == "json":
        return "application/json", (json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")
    if format_name == "csv":
        headers: list[str] = ["schema_version", "dataset", "generated_at", "masked", "filters"]
        for row in rows:
            for key in row.keys():
                if key not in headers:
                    headers.append(key)
        output = io.StringIO()
        writer = csv.DictWriter(output, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            row_with_meta = {
                "schema_version": "phase1.3",
                "dataset": dataset,
                "generated_at": generated_at,
                "masked": True,
                "filters": json.dumps(filters or {}, ensure_ascii=False, sort_keys=True),
                **row,
            }
            writer.writerow({key: _csv_safe_cell(_flatten(row_with_meta.get(key))) for key in headers})
        return "text/csv; charset=utf-8", output.getvalue().encode("utf-8")
    raise ValidationError("format must be csv or json")


def sanitize_download_name(filename: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", filename).strip("-.")
    return cleaned or "export"
