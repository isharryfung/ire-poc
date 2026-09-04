# Phase 1.3 Operational Governance and Demo Readiness

## Scope

Phase 1.3 extends the existing Python/FastAPI JSON+JSONL portal with:

- deterministic demo reset/seed/status workflows
- potential Golden Record duplicate scanning and review queue lifecycle
- read-only storage integrity checks + read-only repair preview
- data quality dashboard metrics from persisted state/events
- masked CSV/JSON exports with CSV formula-injection safety

Out of scope: database/ORM/Redis adoption, auth facade, and Phase 2 identity-graph relationships.

## Architecture notes

- Business logic is implemented in `ire/governance.py`.
- Routers and templates are thin wrappers over service outputs.
- Current-state writes remain atomic JSON writes; history stays append-only JSONL.
- Backward compatibility is preserved through tolerant `from_dict` defaults for new optional models.
- Single-writer file-storage limitations remain.

## CLI quick reference

```bash
python -m ire demo reset --root data/demo-run --yes
python -m ire demo seed --scenario full-showcase --root data/demo-run
python -m ire demo status --root data/demo-run

python -m ire duplicates scan --root data/demo-run
python -m ire duplicates list --status OPEN --root data/demo-run
python -m ire duplicates update DUP-... --status NOT_DUPLICATE --actor demo --reason "verified distinct" --root data/demo-run

python -m ire integrity check --root data/demo-run
python -m ire integrity repair-preview --root data/demo-run

python -m ire data-quality --root data/demo-run
python -m ire export duplicate-candidates --format csv --output output/duplicate-candidates.csv --root data/demo-run
```

## Duplicate governance policy

- scan engine is read-only and reuses existing evidence/scoring primitives
- candidate pairs are canonicalized (`A::B`) and persisted
- no automatic Golden Record merge is performed by duplicate scanning
- duplicate resolution status changes are audited
- `NOT_DUPLICATE` and `DISMISSED` require actor + reason
- merged/superseded version drift marks unresolved candidates stale/merged on refresh

## Data quality metric definitions

- rates use explicit denominators (`records_processed`, `match_runs`) and return `0.0` when denominator is zero
- confidence stats use best-candidate scores from persisted match runs
- source-level drilldown aggregates processed, revision, run, and validation counts by source system

## Integrity checks

Checks include parse validity, key uniqueness, primary-value consistency, orphan links, supersede chain cycles,
merge/rollback/review references, duplicate-candidate references, and status/version consistency where verifiable.

Repair preview is read-only and only proposes safe deterministic categories.

## Export masking and CSV safety

- masked by default (`masked=true` metadata)
- no unmasked web export in this no-auth prototype
- CSV export guards spreadsheet formula injection by prefixing cells that start with `=`, `+`, `-`, `@`
- filenames are sanitized before `Content-Disposition` usage

## Optional reprocessing dry-run

Not included in this PR to keep the required governance scope stable and side-effect-safe.
