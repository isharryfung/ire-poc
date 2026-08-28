# Phase 1 File Storage Design

## Storage model
- JSON files store mutable current state snapshots.
- JSONL files store append-only event/history streams.

## Atomic current-state writes
1. Load and parse existing JSON state (fail if corrupted).
2. Serialize next state and validate JSON format.
3. Backup current state to `<file>.bak`.
4. Write temporary file in same directory.
5. `os.replace()` temp file onto target.

## Corruption behavior
If an existing state JSON file is malformed, writes fail with a clear repository error and the file is not overwritten.

## Idempotency foundation
`SourceRecord` includes `payload_hash`. Repository lookups support:
- external key lookup: `source_system + source_pk`
- exact payload lookup: `source_system + source_pk + payload_hash`

Same external key with different payload hash is allowed for revisions via `supersedes_source_record_id`.

## Single-writer assumption
This foundation assumes a single writer process. Multi-writer locking/consensus is out of scope.
