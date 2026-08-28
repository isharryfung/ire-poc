# Identity Resolution Engine (Phase 1 Foundation)

This repository is now a **Python-only Phase 1 foundation** for the Identity Resolution Engine (IRE).

## Scope now
Implemented in this PR:
- Python package scaffolding (`ire`)
- Typed domain models (dataclasses)
- JSON/JSONL file-storage repository (no database)
- Configuration loader/validator
- ID and UTC timestamp utilities
- Minimal CLI foundation
- Focused unit tests for the foundation storage/config/model behavior

Not implemented yet (future milestones):
- Deterministic/probabilistic matching pipeline execution
- End-to-end merge workflow automation
- Manual review operations workflow
- Phase 2 Relationship Resolution / Identity Graph

## No database dependency
There is **no active Maven/Spring/JPA/Flyway/H2/Oracle/Redis runtime** in this repository.
Legacy Java/Spring implementation details remain available in Git history and summarized under `docs/legacy-java/`.

## Setup
```bash
python -m venv .venv
source .venv/bin/activate
pip install -e .
pip install pytest
```

## Run tests
```bash
pytest
```

## CLI examples
```bash
python -m ire --version
python -m ire init-storage --root /home/runner/work/ire-poc/ire-poc/data
python -m ire validate-config --config-dir /home/runner/work/ire-poc/ire-poc/config
python -m ire validate-storage --root /home/runner/work/ire-poc/ire-poc/data
```

## File-storage layout
- Current state (JSON):
  - `data/state/golden_records.json`
  - `data/state/record_links.json`
  - `data/state/review_tasks.json`
- Append-only events (JSONL):
  - `data/events/source_records.jsonl`
  - `data/events/match_runs.jsonl`
  - `data/events/merge_history.jsonl`
  - `data/events/audit_log.jsonl`

Single-writer MVP assumption: concurrent writers are out of scope in this phase.

## Data safety
Use synthetic sample data only. Do not commit real personal data.
