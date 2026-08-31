"""Seed synthetic Phase 1.2 demo data.

Creates a small set of golden records that exercise the Phase 1.2 features:

* ``GR-DEMO-A`` / ``GR-DEMO-B`` -- two mergeable people records with overlapping
  and conflicting field values (safe to merge, no strong-identifier conflict).
* ``GR-DEMO-C`` / ``GR-DEMO-D`` -- a pair that shares a *different* strong
  identifier (``hkid``) and therefore cannot be merged (blocked).

Usage::

    python scripts/seed_phase12_demo.py --root data/demo-run
"""

from __future__ import annotations

import argparse
from pathlib import Path

from ire.enums import GoldenRecordStatus, LinkStatus
from ire.json_repository import JsonFileRepository
from ire.models import GoldenFieldValue, GoldenRecord, RecordLink

TS = "2026-01-01T00:00:00Z"


def _value(
    raw: str,
    *,
    source: str,
    system: str,
    value_id: str,
    trust: float = 0.9,
    is_primary: bool = False,
    is_verified: bool = True,
    manual_lock: bool = False,
) -> GoldenFieldValue:
    return GoldenFieldValue(
        raw,
        raw.lower(),
        source,
        system,
        trust,
        is_primary,
        is_verified,
        manual_lock,
        True,
        TS,
        value_id=value_id,
    )


def _link(link_id: str, source: str, golden: str) -> RecordLink:
    return RecordLink(link_id, source, golden, LinkStatus.ACTIVE, 0.9, TS, TS)


def _demo_records() -> tuple[list[GoldenRecord], list[RecordLink]]:
    gr_a = GoldenRecord(
        "GR-DEMO-A",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Alice", source="SRC-A1", system="SIS", value_id="GFV-A-fn1", is_primary=True)],
            "last_name": [_value("Chan", source="SRC-A1", system="SIS", value_id="GFV-A-ln1", is_primary=True)],
            "email": [
                _value("alice.chan@example.com", source="SRC-A1", system="SIS", value_id="GFV-A-em1", is_primary=True, trust=0.95),
                _value("a.chan@example.com", source="SRC-A2", system="CRM", value_id="GFV-A-em2", trust=0.6),
            ],
            "emplid": [_value("E1001", source="SRC-A1", system="SIS", value_id="GFV-A-id1", is_primary=True)],
        },
        TS,
        TS,
    )
    gr_b = GoldenRecord(
        "GR-DEMO-B",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Alice", source="SRC-B1", system="CRM", value_id="GFV-B-fn1", is_primary=True)],
            "email": [_value("alice.c@example.com", source="SRC-B1", system="CRM", value_id="GFV-B-em1", is_primary=True, trust=0.7)],
            "phone": [_value("61234567", source="SRC-B1", system="CRM", value_id="GFV-B-ph1", is_primary=True)],
        },
        TS,
        TS,
    )
    gr_c = GoldenRecord(
        "GR-DEMO-C",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Brian", source="SRC-C1", system="SIS", value_id="GFV-C-fn1", is_primary=True)],
            "hkid": [_value("AB1234567", source="SRC-C1", system="SIS", value_id="GFV-C-id1", is_primary=True)],
        },
        TS,
        TS,
    )
    gr_d = GoldenRecord(
        "GR-DEMO-D",
        GoldenRecordStatus.ACTIVE,
        {
            "first_name": [_value("Brian", source="SRC-D1", system="CRM", value_id="GFV-D-fn1", is_primary=True)],
            "hkid": [_value("CD7654321", source="SRC-D1", system="CRM", value_id="GFV-D-id1", is_primary=True)],
        },
        TS,
        TS,
    )
    records = [gr_a, gr_b, gr_c, gr_d]
    links = [
        _link("LINK-A1", "SRC-A1", "GR-DEMO-A"),
        _link("LINK-A2", "SRC-A2", "GR-DEMO-A"),
        _link("LINK-B1", "SRC-B1", "GR-DEMO-B"),
        _link("LINK-C1", "SRC-C1", "GR-DEMO-C"),
        _link("LINK-D1", "SRC-D1", "GR-DEMO-D"),
    ]
    return records, links


def seed(root: Path) -> Path:
    repo = JsonFileRepository(root)
    repo.initialize_storage()
    records, links = _demo_records()
    repo.save_golden_records(records)
    repo.save_record_links(links)
    return root


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed synthetic Phase 1.2 demo data.")
    parser.add_argument("--root", default="data/demo-run", help="Storage root directory (default: data/demo-run)")
    args = parser.parse_args()
    root = seed(Path(args.root))
    print(f"Seeded Phase 1.2 demo data into {root}")
    print("Mergeable pair:  GR-DEMO-A (survivor) + GR-DEMO-B (loser)")
    print("Blocked pair:    GR-DEMO-C + GR-DEMO-D (conflicting hkid)")


if __name__ == "__main__":
    main()
