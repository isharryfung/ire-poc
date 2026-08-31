from __future__ import annotations

from dataclasses import dataclass, field, replace

from .enums import GoldenRecordStatus, HistoryEventType, LinkStatus, MergeEventType
from .exceptions import MergeBlockedError, NotFoundError, StaleVersionError, ValidationError
from .ids import new_audit_event_id, new_merge_event_id, new_rollback_id, utc_now_iso
from .models import (
    AuditEvent,
    GoldenFieldValue,
    GoldenRecord,
    MergeEvent,
    MergeHistoryEvent,
    RecordLink,
    RollbackEvent,
)
from .repository import IRERepository
from .survivorship import _rank

STRONG_IDENTIFIER_FIELDS = ("hkid", "emplid", "student_id", "alumni_id")
DOB_FIELD = "date_of_birth"


# --------------------------------------------------------------------------- #
# Result dataclasses
# --------------------------------------------------------------------------- #
@dataclass
class FieldComparison:
    field_name: str
    left_value: str | None
    right_value: str | None
    status: str  # AGREE / DIFFERENT / CONFLICT / LEFT_ONLY / RIGHT_ONLY
    is_strong_identifier: bool
    is_dob: bool


@dataclass
class GoldenCompareResult:
    left_id: str
    right_id: str
    left: GoldenRecord
    right: GoldenRecord
    fields: list[FieldComparison]
    strong_identifier_conflicts: list[str]
    dob_conflict: bool
    can_merge: bool
    block_reasons: list[str]


@dataclass
class MergePreviewResult:
    survivor_id: str
    loser_id: str
    survivor: GoldenRecord
    loser: GoldenRecord
    can_merge: bool
    block_reasons: list[str]
    merged_fields: dict[str, list[GoldenFieldValue]]
    resulting_primary_values: dict[str, GoldenFieldValue]
    moved_link_ids: list[str]


@dataclass
class MergeResult:
    survivor: GoldenRecord
    loser: GoldenRecord
    merge_event: MergeEvent
    moved_link_ids: list[str]


@dataclass
class RollbackPreviewResult:
    merge_id: str
    survivor_id: str
    loser_id: str
    can_rollback: bool
    block_reasons: list[str]
    restored_survivor: GoldenRecord
    restored_loser: GoldenRecord
    restored_link_ids: list[str]


@dataclass
class RollbackResult:
    survivor: GoldenRecord
    loser: GoldenRecord
    rollback_event: RollbackEvent
    restored_link_ids: list[str]


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def _primary_value(golden: GoldenRecord, field_name: str) -> GoldenFieldValue | None:
    values = golden.fields.get(field_name, [])
    for value in values:
        if value.is_primary and value.is_active:
            return value
    return values[0] if values else None


def _display_value(value: GoldenFieldValue | None) -> str | None:
    if value is None:
        return None
    return value.normalized_value or value.raw_value


def _active_values(golden: GoldenRecord, field_name: str) -> set[str]:
    return {
        (value.normalized_value or value.raw_value)
        for value in golden.fields.get(field_name, [])
        if value.is_active and (value.normalized_value or value.raw_value)
    }


def _has_conflict(left: GoldenRecord, right: GoldenRecord, field_name: str) -> bool:
    left_values = _active_values(left, field_name)
    right_values = _active_values(right, field_name)
    if not left_values or not right_values:
        return False
    return left_values.isdisjoint(right_values)


def _block_reasons(survivor: GoldenRecord, loser: GoldenRecord) -> tuple[list[str], list[str], bool]:
    strong_conflicts = [f for f in STRONG_IDENTIFIER_FIELDS if _has_conflict(survivor, loser, f)]
    dob_conflict = _has_conflict(survivor, loser, DOB_FIELD)
    reasons: list[str] = []
    for field_name in strong_conflicts:
        reasons.append(f"STRONG_IDENTIFIER_CONFLICT:{field_name}")
    if dob_conflict:
        reasons.append("DATE_OF_BIRTH_CONFLICT")
    return reasons, strong_conflicts, dob_conflict


def _dedupe_values(values: list[GoldenFieldValue]) -> list[GoldenFieldValue]:
    seen: set[str] = set()
    result: list[GoldenFieldValue] = []
    for value in values:
        if value.value_id in seen:
            continue
        seen.add(value.value_id)
        result.append(value)
    return result


def _combine_field(
    field_name: str,
    survivor_values: list[GoldenFieldValue],
    loser_values: list[GoldenFieldValue],
    proposed_selection: str | None,
) -> list[GoldenFieldValue]:
    combined = _dedupe_values(list(survivor_values) + list(loser_values))
    if not combined:
        return combined

    primary_value_id: str | None = None
    lock_selected = False

    if proposed_selection is not None:
        if not any(value.value_id == proposed_selection for value in combined):
            raise ValidationError(
                f"proposed primary value_id not found for field {field_name}: {proposed_selection}"
            )
        primary_value_id = proposed_selection
        lock_selected = True
    elif any(value.manual_lock for value in combined):
        # Preserve a manually locked selection instead of recomputing survivorship.
        locked_primary = next(
            (value for value in combined if value.manual_lock and value.is_primary),
            None,
        )
        if locked_primary is None:
            locked_primary = next(value for value in combined if value.manual_lock)
        primary_value_id = locked_primary.value_id
    else:
        active = [value for value in combined if value.is_active] or combined
        winner = max(active, key=_rank)
        primary_value_id = winner.value_id

    result: list[GoldenFieldValue] = []
    for value in combined:
        is_primary = value.value_id == primary_value_id
        manual_lock = value.manual_lock or (lock_selected and is_primary)
        result.append(replace(value, is_primary=is_primary, manual_lock=manual_lock))
    return result


def _combine_fields(
    survivor: GoldenRecord,
    loser: GoldenRecord,
    proposed_selections: dict[str, str] | None,
) -> dict[str, list[GoldenFieldValue]]:
    proposed = proposed_selections or {}
    merged: dict[str, list[GoldenFieldValue]] = {}
    for field_name in list(survivor.fields.keys()) + [f for f in loser.fields if f not in survivor.fields]:
        merged[field_name] = _combine_field(
            field_name,
            survivor.fields.get(field_name, []),
            loser.fields.get(field_name, []),
            proposed.get(field_name),
        )
    return merged


def _primary_values(fields: dict[str, list[GoldenFieldValue]]) -> dict[str, GoldenFieldValue]:
    primaries: dict[str, GoldenFieldValue] = {}
    for field_name, values in fields.items():
        primary = next((value for value in values if value.is_primary and value.is_active), None)
        if primary is None and values:
            primary = values[0]
        if primary is not None:
            primaries[field_name] = primary
    return primaries


def _require_active_pair(survivor_id: str, loser_id: str, repo: IRERepository) -> tuple[GoldenRecord, GoldenRecord]:
    if survivor_id == loser_id:
        raise ValidationError("survivor and loser must be different golden records")
    survivor = repo.find_golden_record(survivor_id)
    if survivor is None:
        raise NotFoundError(f"golden record not found: {survivor_id}")
    loser = repo.find_golden_record(loser_id)
    if loser is None:
        raise NotFoundError(f"golden record not found: {loser_id}")
    return survivor, loser


# --------------------------------------------------------------------------- #
# Compare
# --------------------------------------------------------------------------- #
def compare_golden_records(left_id: str, right_id: str, repo: IRERepository) -> GoldenCompareResult:
    if left_id == right_id:
        raise ValidationError("cannot compare a golden record with itself")
    left = repo.find_golden_record(left_id)
    if left is None:
        raise NotFoundError(f"golden record not found: {left_id}")
    right = repo.find_golden_record(right_id)
    if right is None:
        raise NotFoundError(f"golden record not found: {right_id}")

    field_names = list(left.fields.keys()) + [f for f in right.fields if f not in left.fields]
    comparisons: list[FieldComparison] = []
    for field_name in field_names:
        left_value = _display_value(_primary_value(left, field_name))
        right_value = _display_value(_primary_value(right, field_name))
        is_strong = field_name in STRONG_IDENTIFIER_FIELDS
        is_dob = field_name == DOB_FIELD
        if left_value is None and right_value is None:
            continue
        if left_value is not None and right_value is None:
            status = "LEFT_ONLY"
        elif left_value is None and right_value is not None:
            status = "RIGHT_ONLY"
        elif left_value == right_value:
            status = "AGREE"
        elif _has_conflict(left, right, field_name) and (is_strong or is_dob):
            status = "CONFLICT"
        else:
            status = "DIFFERENT"
        comparisons.append(
            FieldComparison(
                field_name=field_name,
                left_value=left_value,
                right_value=right_value,
                status=status,
                is_strong_identifier=is_strong,
                is_dob=is_dob,
            )
        )

    reasons, strong_conflicts, dob_conflict = _block_reasons(left, right)
    return GoldenCompareResult(
        left_id=left_id,
        right_id=right_id,
        left=left,
        right=right,
        fields=comparisons,
        strong_identifier_conflicts=strong_conflicts,
        dob_conflict=dob_conflict,
        can_merge=not reasons,
        block_reasons=reasons,
    )


# --------------------------------------------------------------------------- #
# Merge preview (side-effect free)
# --------------------------------------------------------------------------- #
def preview_golden_merge(
    survivor_id: str,
    loser_id: str,
    repo: IRERepository,
    proposed_selections: dict[str, str] | None = None,
) -> MergePreviewResult:
    survivor, loser = _require_active_pair(survivor_id, loser_id, repo)

    block_reasons: list[str] = []
    if survivor.status == GoldenRecordStatus.SUPERSEDED or survivor.superseded_by is not None:
        block_reasons.append(f"SURVIVOR_SUPERSEDED:{survivor_id}")
    if loser.status == GoldenRecordStatus.SUPERSEDED or loser.superseded_by is not None:
        block_reasons.append(f"LOSER_SUPERSEDED:{loser_id}")
    block_reasons.extend(_block_reasons(survivor, loser)[0])

    merged_fields = _combine_fields(survivor, loser, proposed_selections)
    moved_link_ids = [
        link.link_id
        for link in repo.load_record_links()
        if link.golden_record_id == loser_id and link.status == LinkStatus.ACTIVE
    ]

    return MergePreviewResult(
        survivor_id=survivor_id,
        loser_id=loser_id,
        survivor=survivor,
        loser=loser,
        can_merge=not block_reasons,
        block_reasons=block_reasons,
        merged_fields=merged_fields,
        resulting_primary_values=_primary_values(merged_fields),
        moved_link_ids=moved_link_ids,
    )


# --------------------------------------------------------------------------- #
# Merge (state changing)
# --------------------------------------------------------------------------- #
def merge_golden_records(
    survivor_id: str,
    loser_id: str,
    actor: str,
    reason: str,
    repo: IRERepository,
    expected_survivor_version: int | None = None,
    expected_loser_version: int | None = None,
    proposed_selections: dict[str, str] | None = None,
) -> MergeResult:
    survivor, loser = _require_active_pair(survivor_id, loser_id, repo)

    if survivor.status == GoldenRecordStatus.SUPERSEDED or survivor.superseded_by is not None:
        raise ValidationError(f"survivor is already superseded: {survivor_id}")
    if loser.status == GoldenRecordStatus.SUPERSEDED or loser.superseded_by is not None:
        raise ValidationError(f"loser is already superseded: {loser_id}")

    if expected_survivor_version is not None and expected_survivor_version != survivor.version:
        raise StaleVersionError(
            f"survivor version mismatch: expected {expected_survivor_version}, found {survivor.version}"
        )
    if expected_loser_version is not None and expected_loser_version != loser.version:
        raise StaleVersionError(
            f"loser version mismatch: expected {expected_loser_version}, found {loser.version}"
        )

    reasons, _, _ = _block_reasons(survivor, loser)
    if reasons:
        raise MergeBlockedError("; ".join(reasons))

    now = utc_now_iso()
    survivor_before = survivor.to_dict()
    loser_before = loser.to_dict()

    merged_fields = _combine_fields(survivor, loser, proposed_selections)
    survivor_after = replace(
        survivor,
        fields=merged_fields,
        status=GoldenRecordStatus.ACTIVE,
        updated_at=now,
        version=survivor.version + 1,
        merged_from=list(survivor.merged_from) + [loser_id],
    )
    loser_after = replace(
        loser,
        status=GoldenRecordStatus.SUPERSEDED,
        superseded_by=survivor_id,
        updated_at=now,
        version=loser.version + 1,
    )

    links = repo.load_record_links()
    survivor_active_sources = {
        link.source_record_id
        for link in links
        if link.golden_record_id == survivor_id and link.status == LinkStatus.ACTIVE
    }
    affected_links = [link for link in links if link.golden_record_id == loser_id]
    links_before = [link.to_dict() for link in affected_links]
    updated_links: list[RecordLink] = []
    moved_link_ids: list[str] = []
    for link in affected_links:
        if link.status == LinkStatus.ACTIVE and link.source_record_id in survivor_active_sources:
            updated_links.append(replace(link, status=LinkStatus.INACTIVE, updated_at=now))
        else:
            updated_links.append(replace(link, golden_record_id=survivor_id, updated_at=now))
        moved_link_ids.append(link.link_id)

    merge_event = MergeEvent(
        merge_id=new_merge_event_id(),
        survivor_id=survivor_id,
        loser_id=loser_id,
        actor=actor,
        reason=reason,
        timestamp=now,
        survivor_before=survivor_before,
        survivor_after=survivor_after.to_dict(),
        loser_before=loser_before,
        links_before=links_before,
    )
    merge_history = MergeHistoryEvent(
        merge_event_id=merge_event.merge_id,
        event_type=MergeEventType.MANUAL,
        winner_golden_record_id=survivor_id,
        loser_golden_record_id=loser_id,
        reason=reason,
        created_at=now,
    )
    audit_event = AuditEvent(
        audit_event_id=new_audit_event_id(),
        event_type=HistoryEventType.GOLDEN_MERGE.value,
        entity_type="GoldenRecord",
        entity_id=survivor_id,
        actor=actor,
        details={
            "merge_id": merge_event.merge_id,
            "loser_golden_record_id": loser_id,
            "reason": reason,
            "moved_link_ids": moved_link_ids,
            "survivor_version": survivor_after.version,
        },
        created_at=now,
    )

    with repo.atomic_update():
        repo.save_golden_record(survivor_after)
        repo.save_golden_record(loser_after)
        for link in updated_links:
            repo.save_record_link(link)
        repo.save_merge_event(merge_event)
        repo.append_merge_history_event(merge_history)
        repo.append_audit_event(audit_event)

    return MergeResult(
        survivor=survivor_after,
        loser=loser_after,
        merge_event=merge_event,
        moved_link_ids=moved_link_ids,
    )


# --------------------------------------------------------------------------- #
# Rollback
# --------------------------------------------------------------------------- #
def _rollback_targets(event: MergeEvent, repo: IRERepository) -> tuple[GoldenRecord, GoldenRecord, list[RecordLink], list[str]]:
    now = utc_now_iso()
    survivor_snapshot = GoldenRecord.from_dict(event.survivor_before)
    loser_snapshot = GoldenRecord.from_dict(event.loser_before)

    survivor_current = repo.find_golden_record(event.survivor_id)
    loser_current = repo.find_golden_record(event.loser_id)
    survivor_version = (survivor_current.version if survivor_current else survivor_snapshot.version) + 1
    loser_version = (loser_current.version if loser_current else loser_snapshot.version) + 1

    restored_survivor = replace(survivor_snapshot, updated_at=now, version=survivor_version)
    restored_loser = replace(loser_snapshot, updated_at=now, version=loser_version)

    restored_links = [replace(RecordLink.from_dict(snap), updated_at=now) for snap in event.links_before]
    restored_link_ids = [link.link_id for link in restored_links]
    return restored_survivor, restored_loser, restored_links, restored_link_ids


def rollback_merge_preview(merge_id: str, repo: IRERepository) -> RollbackPreviewResult:
    event = repo.load_merge_event(merge_id)
    if event is None:
        raise NotFoundError(f"merge event not found: {merge_id}")

    block_reasons: list[str] = []
    if repo.find_rollback_for_merge(merge_id) is not None:
        block_reasons.append("MERGE_ALREADY_ROLLED_BACK")

    survivor_current = repo.find_golden_record(event.survivor_id)
    if survivor_current is not None and (
        survivor_current.status == GoldenRecordStatus.SUPERSEDED or survivor_current.superseded_by is not None
    ):
        block_reasons.append(f"SURVIVOR_SUPERSEDED:{event.survivor_id}")

    loser_current = repo.find_golden_record(event.loser_id)
    if loser_current is not None and loser_current.superseded_by not in (None, event.survivor_id):
        block_reasons.append("LOSER_STATE_CHANGED")

    restored_survivor, restored_loser, _, restored_link_ids = _rollback_targets(event, repo)
    return RollbackPreviewResult(
        merge_id=merge_id,
        survivor_id=event.survivor_id,
        loser_id=event.loser_id,
        can_rollback=not block_reasons,
        block_reasons=block_reasons,
        restored_survivor=restored_survivor,
        restored_loser=restored_loser,
        restored_link_ids=restored_link_ids,
    )


def rollback_merge(merge_id: str, actor: str, reason: str, repo: IRERepository) -> RollbackResult:
    event = repo.load_merge_event(merge_id)
    if event is None:
        raise NotFoundError(f"merge event not found: {merge_id}")
    if repo.find_rollback_for_merge(merge_id) is not None:
        raise ValidationError(f"merge already rolled back: {merge_id}")

    now = utc_now_iso()
    restored_survivor, restored_loser, restored_links, restored_link_ids = _rollback_targets(event, repo)

    rollback_event = RollbackEvent(
        rollback_id=new_rollback_id(),
        merge_id=merge_id,
        survivor_id=event.survivor_id,
        loser_id=event.loser_id,
        actor=actor,
        reason=reason,
        timestamp=now,
    )
    merge_history = MergeHistoryEvent(
        merge_event_id=merge_id,
        event_type=MergeEventType.UNMERGE,
        winner_golden_record_id=event.survivor_id,
        loser_golden_record_id=event.loser_id,
        reason=reason,
        created_at=now,
    )
    audit_event = AuditEvent(
        audit_event_id=new_audit_event_id(),
        event_type=HistoryEventType.MERGE_ROLLBACK.value,
        entity_type="GoldenRecord",
        entity_id=event.survivor_id,
        actor=actor,
        details={
            "merge_id": merge_id,
            "rollback_id": rollback_event.rollback_id,
            "loser_golden_record_id": event.loser_id,
            "reason": reason,
            "restored_link_ids": restored_link_ids,
        },
        created_at=now,
    )

    with repo.atomic_update():
        repo.save_golden_record(restored_survivor)
        repo.save_golden_record(restored_loser)
        for link in restored_links:
            repo.save_record_link(link)
        repo.save_rollback_event(rollback_event)
        repo.append_merge_history_event(merge_history)
        repo.append_audit_event(audit_event)

    return RollbackResult(
        survivor=restored_survivor,
        loser=restored_loser,
        rollback_event=rollback_event,
        restored_link_ids=restored_link_ids,
    )
