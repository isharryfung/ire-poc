# Identity Resolution Engine (Phase 1.1 Demo)

This repository is a **Python-only Phase 1 Identity Resolution Engine** with a local **FastAPI demonstration layer**.

## Scope now
Implemented in Phase 1 / Phase 1.1:
- Python package scaffolding (`ire`)
- Typed domain models and JSON/JSONL file storage
- Configuration loader/validator
- Phase 1 ingestion, validation, matching, decisioning, survivorship, and manual review workflow
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
  - `events/audit_log.jsonl`

See `docs/demo-guide.md` for the local demo guide.
