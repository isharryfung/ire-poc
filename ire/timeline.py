from __future__ import annotations

from .enums import HistoryEventType
from .exceptions import NotFoundError
from .models import GoldenRecord, TimelineEntry
from .repository import IRERepository

CATEGORY_LIFECYCLE = "LIFECYCLE"
CATEGORY_MERGE = "MERGE"
CATEGORY_OVERRIDE = "OVERRIDE"
CATEGORY_ROLLBACK = "ROLLBACK"
CATEGORY_LINK = "LINK"
CATEGORY_AUDIT = "AUDIT"

_AUDIT_CATEGORY = {
    HistoryEventType.PRIMARY_OVERRIDE.value: CATEGORY_OVERRIDE,
    HistoryEventType.GOLDEN_MERGE.value: CATEGORY_MERGE,
    HistoryEventType.MERGE_ROLLBACK.value: CATEGORY_ROLLBACK,
}


def get_golden_timeline(
    golden_id: str,
    repo: IRERepository,
    category_filter: str | None = None,
) -> list[TimelineEntry]:
    """Build a chronological, side-effect-free timeline for a golden record.

    Summaries never expose raw field values; overrides are described by field
    name and value id only so sensitive data is not leaked through history.
    """
    golden = repo.find_golden_record(golden_id)
    if golden is None:
        raise NotFoundError(f"golden record not found: {golden_id}")

    entries: list[TimelineEntry] = []
    entries.append(
        TimelineEntry(
            timestamp=golden.created_at,
            category=CATEGORY_LIFECYCLE,
            event_type="GOLDEN_CREATED",
            summary=f"Golden record {golden_id} created",
            actor="system",
            details={"status": golden.status.value, "version": golden.version},
        )
    )

    _add_link_entries(golden_id, repo, entries)
    _add_merge_entries(golden_id, repo, entries)
    _add_override_entries(golden_id, repo, entries)
    _add_rollback_entries(golden_id, repo, entries)
    _add_audit_entries(golden_id, repo, entries)

    if category_filter is not None:
        entries = [entry for entry in entries if entry.category == category_filter]

    entries.sort(key=lambda entry: entry.timestamp, reverse=True)
    return entries


def _add_link_entries(golden_id: str, repo: IRERepository, entries: list[TimelineEntry]) -> None:
    for link in repo.load_record_links():
        if link.golden_record_id != golden_id:
            continue
        entries.append(
            TimelineEntry(
                timestamp=link.created_at,
                category=CATEGORY_LINK,
                event_type="LINK_CREATED",
                summary=f"Source record {link.source_record_id} linked ({link.status.value})",
                actor="system",
                details={
                    "link_id": link.link_id,
                    "source_record_id": link.source_record_id,
                    "status": link.status.value,
                },
            )
        )


def _add_merge_entries(golden_id: str, repo: IRERepository, entries: list[TimelineEntry]) -> None:
    for event in repo.load_merge_events():
        if golden_id not in (event.survivor_id, event.loser_id):
            continue
        role = "survivor" if event.survivor_id == golden_id else "loser"
        summary = (
            f"Merged {event.loser_id} into {event.survivor_id} (this record was the {role})"
        )
        entries.append(
            TimelineEntry(
                timestamp=event.timestamp,
                category=CATEGORY_MERGE,
                event_type=HistoryEventType.GOLDEN_MERGE.value,
                summary=summary,
                actor=event.actor,
                details={
                    "merge_id": event.merge_id,
                    "survivor_id": event.survivor_id,
                    "loser_id": event.loser_id,
                    "reason": event.reason,
                    "role": role,
                },
            )
        )


def _add_override_entries(golden_id: str, repo: IRERepository, entries: list[TimelineEntry]) -> None:
    for event in repo.load_primary_override_events():
        if event.golden_record_id != golden_id:
            continue
        entries.append(
            TimelineEntry(
                timestamp=event.timestamp,
                category=CATEGORY_OVERRIDE,
                event_type=HistoryEventType.PRIMARY_OVERRIDE.value,
                summary=f"Primary value for '{event.field_name}' set to {event.value_id}",
                actor=event.actor,
                details={
                    "override_id": event.override_id,
                    "field_name": event.field_name,
                    "value_id": event.value_id,
                    "previous_value_id": event.previous_value_id,
                    "reason": event.reason,
                },
            )
        )


def _add_rollback_entries(golden_id: str, repo: IRERepository, entries: list[TimelineEntry]) -> None:
    for event in repo.load_rollback_events():
        if golden_id not in (event.survivor_id, event.loser_id):
            continue
        entries.append(
            TimelineEntry(
                timestamp=event.timestamp,
                category=CATEGORY_ROLLBACK,
                event_type=HistoryEventType.MERGE_ROLLBACK.value,
                summary=f"Merge {event.merge_id} rolled back",
                actor=event.actor,
                details={
                    "rollback_id": event.rollback_id,
                    "merge_id": event.merge_id,
                    "survivor_id": event.survivor_id,
                    "loser_id": event.loser_id,
                    "reason": event.reason,
                },
            )
        )


def _add_audit_entries(golden_id: str, repo: IRERepository, entries: list[TimelineEntry]) -> None:
    covered = {
        HistoryEventType.PRIMARY_OVERRIDE.value,
        HistoryEventType.GOLDEN_MERGE.value,
        HistoryEventType.MERGE_ROLLBACK.value,
    }
    for event in repo.load_audit_events():
        if event.entity_id != golden_id:
            continue
        if event.event_type in covered:
            # Already represented by the dedicated event stream above.
            continue
        entries.append(
            TimelineEntry(
                timestamp=event.created_at,
                category=_AUDIT_CATEGORY.get(event.event_type, CATEGORY_AUDIT),
                event_type=event.event_type,
                summary=f"{event.event_type} on {event.entity_id}",
                actor=event.actor,
                details=dict(event.details),
            )
        )
