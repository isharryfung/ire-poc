# Identity Resolution Engine (Phase 1.2 Demo)

This repository is a **Python-only Phase 1 Identity Resolution Engine** with a local **FastAPI demonstration layer**.

## Scope now
Implemented in Phase 1 / Phase 1.1 / Phase 1.2:
- Python package scaffolding (`ire`)
- Typed domain models and JSON/JSONL file storage
- Configuration loader/validator
- Phase 1 ingestion, validation, matching, decisioning, survivorship, and manual review workflow
- **Phase 1.2 Identity Correction and Golden Record Consolidation** (see below)
- CLI commands for processing, preview, review, Golden Record inspection, and the local FastAPI demo
- FastAPI + Jinja2 browser demo backed by the existing Phase 1 services

Explicitly out of scope:
- Any legacy Java/Spring/database runtime
- Production authentication/authorization
- Phase 2 Relationship Resolution / Identity Graph

## No database dependency
There is **no database dependency** in the active runtime. The demo uses:
- JSON state files under `state/`
- JSONL event files under `events/`

Single-writer/file-storage limitations still apply.

## Setup
```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
```

## Run tests
```bash
python -m pytest
```

## CLI examples
```bash
python -m ire --version
python -m ire storage init --root data/demo-run
python -m ire process --input '{"source_system":"SIS","source_pk":"1","data":{"emplid":"E1"}}' --root data/demo-run
python -m ire preview --input '{"source_system":"SIS","source_pk":"1","data":{"emplid":"E1"}}' --root data/demo-run
python -m ire review list --root data/demo-run
python -m ire golden show GR-EXAMPLE --root data/demo-run
```

## Phase 1.2 Identity Correction and Golden Record Consolidation
Phase 1.2 adds six capabilities on top of Phase 1/1.1, all implemented in domain
services (`ire/primary_override.py`, `ire/golden_merge.py`, `ire/timeline.py`) with
atomic JSON state writes and append-only JSONL audit/merge events:

1. **Field-level Primary Value Override** – manually promote any retained value to
   primary. The chosen value is `manual_lock`-ed so later survivorship keeps it.
2. **Golden Record Compare** – field-by-field agreement/conflict report with
   strong-identifier and date-of-birth conflict flags.
3. **Merge Preview** – side-effect-free preview of the survivorship outcome and the
   links that would move.
4. **Golden-to-Golden Merge** – consolidate a loser into a survivor. The loser is
   marked `SUPERSEDED` with `superseded_by` set (never deleted); all known values and
   source provenance are preserved.
5. **Safe Merge Rollback** – revert a merge, restoring both records and their links.
6. **Golden Record Timeline** – aggregated lifecycle, link, merge, override, and
   rollback history for a single golden record.

### Merge safety rules
- **Strong identifier conflict blocking** – a merge is blocked when both records have
  non-empty, *different* values for any of `hkid`, `emplid`, `student_id`, `alumni_id`.
- **DOB conflict blocking** – a merge is blocked when both records have different
  non-empty `date_of_birth` values.
- **Manual lock preservation** – survivorship never overwrites a `manual_lock`-ed field.
- **Optimistic concurrency** – `version` is incremented on every state change; passing a
  stale `expected_version` raises a `StaleVersionError` (HTTP 409).

### Seed synthetic demo data
```bash
python scripts/seed_phase12_demo.py --root data/demo-run
```
This creates a mergeable pair (`GR-DEMO-A` survivor + `GR-DEMO-B` loser) and a blocked
pair with conflicting `hkid` (`GR-DEMO-C` + `GR-DEMO-D`).

### Phase 1.2 CLI commands
```bash
python -m ire golden compare GR-DEMO-A GR-DEMO-B --root data/demo-run
python -m ire golden merge-preview --survivor GR-DEMO-A --loser GR-DEMO-B --root data/demo-run
python -m ire golden merge --survivor GR-DEMO-A --loser GR-DEMO-B --actor harry --reason "dedupe" --root data/demo-run
python -m ire golden override-primary GR-DEMO-A --field email --value-id GFV-A-em2 --actor harry --reason "verified" --root data/demo-run
python -m ire golden rollback-preview MERGE-XXXXXXXX --root data/demo-run
python -m ire golden rollback MERGE-XXXXXXXX --actor harry --reason "undo" --root data/demo-run
python -m ire golden timeline GR-DEMO-A --root data/demo-run
```

### Phase 1.2 browser pages
- Compare two golden records at `/golden-records/compare`
- Preview and execute a merge at `/golden-records/merge/preview`
- Roll a merge back at `/golden-records/merge/{merge_id}/rollback-preview`
- Override a primary value and view the timeline from a record's detail page at
  `/golden-records/{golden_id}`

## Start the local web demo
Always bind to localhost only.

```bash
python -m ire web --host 127.0.0.1 --port 8000 --root data/demo-run --config-dir config
```

Optional direct Uvicorn launch:
```bash
uvicorn ire.web.app:create_app --factory --host 127.0.0.1 --port 8000
```

Local URLs:
- App: `http://127.0.0.1:8000/`
- OpenAPI docs: `http://127.0.0.1:8000/docs`

## Demo safety warning
This demo has **no production authentication** and **must not be exposed publicly**. Use synthetic sample data only.

## Browser demo workflow
- Open the dashboard at `/`
- Submit one record at `/identities/new`
- Upload a CSV/JSON batch at `/identities/batch`
- Inspect Golden Records at `/golden-records`
- Process pending manual reviews at `/reviews`

## API routes
Key REST endpoints live under `/api/v1`:
- `GET /api/v1/health`
- `POST /api/v1/identities/process`
- `POST /api/v1/identities/preview`
- `POST /api/v1/identities/batch`
- `GET /api/v1/golden-records`
- `GET /api/v1/golden-records/{golden_id}`
- `GET /api/v1/reviews`
- `GET /api/v1/reviews/{task_id}`
- `POST /api/v1/reviews/{task_id}/approve`
- `POST /api/v1/reviews/{task_id}/reject`

Phase 1.2 REST endpoints:
- `GET /api/v1/golden-records/compare?left=GR-A&right=GR-B`
- `POST /api/v1/golden-records/{golden_id}/primary-values/{field_name}`
- `POST /api/v1/golden-records/merge/preview`
- `POST /api/v1/golden-records/merge`
- `GET /api/v1/golden-records/merge/{merge_id}/rollback-preview`
- `POST /api/v1/golden-records/merge/{merge_id}/rollback`
- `GET /api/v1/golden-records/{golden_id}/timeline`

## CSV upload format
CSV uploads should include `source_system` and `source_pk` columns plus any supported identity fields, for example:

```csv
source_system,source_pk,emplid,first_name,last_name,email,phone,date_of_birth
SIS,SIS-10001,100001,Siu Mei,Chan,smchan@example.edu,8567-4123,1985-05-20
```

## Isolated demo storage and reset
Use a dedicated root such as `data/demo-run` or a temporary directory for tests and demos.

```bash
rm -rf data/demo-run
python -m ire storage init --root data/demo-run
```

## File-storage layout
- Current state (JSON):
  - `state/golden_records.json`
  - `state/record_links.json`
  - `state/review_tasks.json`
- Append-only events (JSONL):
  - `events/source_records.jsonl`
  - `events/match_runs.jsonl`
  - `events/merge_history.jsonl`
  - `events/merge_events.jsonl`
  - `events/primary_overrides.jsonl`
  - `events/merge_rollbacks.jsonl`
  - `events/audit_log.jsonl`

See `docs/demo-guide.md` for the local demo guide.
