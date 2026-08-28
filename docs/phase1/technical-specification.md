# Phase 1 Technical Specification (Python Foundation)

## Purpose
Phase 1 resolves records referring to the same person into a Golden Record with explainable, auditable decisions.

## In-scope for this foundation PR
- Python package architecture
- Typed domain models
- Enums, exceptions, ID/timestamp utilities
- Config loader and validation
- JSON/JSONL repository abstraction and implementation
- Minimal CLI for version/config/storage checks
- Unit/integration tests for foundation behaviors

## Out of scope in this PR
- Full deterministic/probabilistic matching execution
- Automated merge/survivorship workflow execution
- Manual-review operations workflow execution
- Phase 2 relationship resolution / identity graph

## Module structure
- `ire.enums`: controlled vocabularies/statuses
- `ire.exceptions`: application exceptions
- `ire.ids`: prefixed IDs + UTC timestamps
- `ire.models`: dataclasses and JSON conversion helpers
- `ire.config`: immutable validated config objects
- `ire.repository`: repository interface/protocol
- `ire.json_repository`: JSON/JSONL file repository
- `ire.cli` and `ire.__main__`: command-line entry points

## Core models
- SourceSystem, SourceRecord, NormalizedIdentity
- GoldenFieldValue, GoldenRecord, RecordLink
- MatchFeature, MatchCandidate, MatchRun, MatchDecision
- ManualReviewTask, ManualReviewDecision
- MergeHistoryEvent, AuditEvent

## Policy and decision framework (configuration)
- Matching thresholds: auto_merge=0.85, manual_review=0.50, candidate_gap=0.10
- Field priorities and algorithm names
- Candidate limits
- Safety settings
- Survivorship field strategies + manual-lock protection

## JSON formats
- State files are JSON arrays of model objects.
- Event streams are JSONL, one serialized event object per line.
- All timestamps are UTC ISO-8601 with trailing `Z`.
- Confidence/similarity values are constrained to [0.0, 1.0].

## Survivorship principles (planned integration)
- Keep all known values by field with provenance and trust metadata.
- Protect manually locked values from automated overwrite.
- Allow primary/verified markers and active/inactive value lifecycle.

## CLI roadmap
Implemented now:
- `--version`
- `init-storage`
- `validate-config`
- `validate-storage`

Planned later:
- `ingest`
- `match-run`
- `review`
- `merge`

These later commands are intentionally not implemented in this foundation.

## Testing strategy in this PR
- Config happy-path and failure-path validation tests
- Model round-trip serialization tests
- Prefixed ID and UTC timestamp tests
- Repository initialization and JSONL append tests
- Atomic write and backup behavior tests
- Duplicate external payload detection tests
- Corrupted JSON read/write protection tests

## Acceptance scenarios covered
- Project installs/runs as Python package
- No DB runtime requirement
- Config validation and clear failure behavior
- State/event storage initialization
- Atomic + backup writes with corruption protection
- Model serialization/deserialization

## Phase 2 integration contract (excluded from implementation)
Phase 2 will add relationship resolution and identity graph capabilities as an extension layer that consumes Phase 1 Golden Records and auditable identity events without changing the Phase 1 storage contract.
