from datetime import datetime

from ire.ids import new_audit_event_id, new_golden_record_id, new_source_record_id, utc_now_iso


def test_id_prefixes() -> None:
    assert new_source_record_id().startswith("SRC-")
    assert new_golden_record_id().startswith("GR-")
    assert new_audit_event_id().startswith("AUD-")


def test_utc_timestamp_format() -> None:
    ts = utc_now_iso()
    assert ts.endswith("Z")
    datetime.fromisoformat(ts.replace("Z", "+00:00"))
