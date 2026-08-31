from __future__ import annotations

from dataclasses import dataclass, replace

from .enums import GoldenRecordStatus, HistoryEventType
from .exceptions import NotFoundError, ValidationError
from .ids import new_audit_event_id, new_primary_override_id, utc_now_iso
from .models import AuditEvent, GoldenFieldValue, GoldenRecord, PrimaryOverrideEvent
from .repository import IRERepository


@dataclass
class PrimaryOverrideResult:
    golden_record: GoldenRecord
    override_event: PrimaryOverrideEvent
    field_name: str
    previous_primary_value_id: str | None
    new_primary_value_id: str


def _current_primary_value_id(values: list[GoldenFieldValue]) -> str | None:
    for value in values:
        if value.is_primary and value.is_active:
            return value.value_id
    return None


def override_primary_value(
    golden_id: str,
    field_name: str,
    value_id: str,
    actor: str,
    reason: str,
    repo: IRERepository,
) -> PrimaryOverrideResult:
    """Manually select which retained value is the primary output for a field.

    The chosen value is pinned with ``manual_lock`` so subsequent survivorship
    passes preserve the operator's decision. All values are retained (no data
    is deleted); only the primary/lock flags change.
    """
    golden = repo.find_golden_record(golden_id)
    if golden is None:
        raise NotFoundError(f"golden record not found: {golden_id}")
    if golden.status == GoldenRecordStatus.SUPERSEDED or golden.superseded_by is not None:
        raise ValidationError(f"cannot override a superseded golden record: {golden_id}")

    values = list(golden.fields.get(field_name, []))
    if not values:
        raise NotFoundError(f"field not present on golden record: {field_name}")

    target = next((value for value in values if value.value_id == value_id), None)
    if target is None:
        raise NotFoundError(f"value_id not found for field {field_name}: {value_id}")
    if not target.is_active:
        raise ValidationError(f"cannot set an inactive value as primary: {value_id}")

    previous_primary_value_id = _current_primary_value_id(values)

    updated_values = [
        replace(value, is_primary=value.value_id == value_id, manual_lock=value.value_id == value_id)
        for value in values
    ]
    updated_fields = {name: list(field_values) for name, field_values in golden.fields.items()}
    updated_fields[field_name] = updated_values

    now = utc_now_iso()
    updated_golden = replace(
        golden,
        fields=updated_fields,
        updated_at=now,
        version=golden.version + 1,
    )

    override_event = PrimaryOverrideEvent(
        override_id=new_primary_override_id(),
        golden_record_id=golden_id,
        field_name=field_name,
        value_id=value_id,
        previous_value_id=previous_primary_value_id,
        actor=actor,
        reason=reason,
        timestamp=now,
    )
    audit_event = AuditEvent(
        audit_event_id=new_audit_event_id(),
        event_type=HistoryEventType.PRIMARY_OVERRIDE.value,
        entity_type="GoldenRecord",
        entity_id=golden_id,
        actor=actor,
        details={
            "field_name": field_name,
            "value_id": value_id,
            "previous_value_id": previous_primary_value_id,
            "reason": reason,
            "version": updated_golden.version,
        },
        created_at=now,
    )

    with repo.atomic_update():
        repo.save_golden_record(updated_golden)
        repo.save_primary_override_event(override_event)
        repo.append_audit_event(audit_event)

    return PrimaryOverrideResult(
        golden_record=updated_golden,
        override_event=override_event,
        field_name=field_name,
        previous_primary_value_id=previous_primary_value_id,
        new_primary_value_id=value_id,
    )
