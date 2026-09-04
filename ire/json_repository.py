from __future__ import annotations

import json
import shutil
from contextlib import contextmanager
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any, Callable, Iterator

from .exceptions import DuplicateSourceRecordError, RepositoryError
from .models import (
    AuditEvent,
    DemoScenarioManifest,
    DuplicateCandidate,
    DuplicateScanRun,
    GoldenRecord,
    IntegrityFinding,
    ManualReviewTask,
    MatchRun,
    MergeEvent,
    MergeHistoryEvent,
    PrimaryOverrideEvent,
    RecordLink,
    RollbackEvent,
    SourceRecord,
)


class JsonFileRepository:
    """Single-writer JSON/JSONL repository for the Phase 1 MVP.

    Source-record duplicate checks currently use a linear JSONL scan as an
    intentional Phase 1 foundation trade-off.
    """

    def __init__(self, root_dir: str | Path) -> None:
        self.root = Path(root_dir)
        self.state_dir = self.root / "state"
        self.events_dir = self.root / "events"

        self.golden_records_path = self.state_dir / "golden_records.json"
        self.record_links_path = self.state_dir / "record_links.json"
        self.review_tasks_path = self.state_dir / "review_tasks.json"
        self.duplicate_candidates_path = self.state_dir / "duplicate_candidates.json"
        self.demo_manifest_path = self.state_dir / "demo_manifest.json"

        self.source_records_path = self.events_dir / "source_records.jsonl"
        self.match_runs_path = self.events_dir / "match_runs.jsonl"
        self.merge_history_path = self.events_dir / "merge_history.jsonl"
        self.audit_log_path = self.events_dir / "audit_log.jsonl"
        self.merge_events_path = self.events_dir / "merge_events.jsonl"
        self.primary_overrides_path = self.events_dir / "primary_overrides.jsonl"
        self.merge_rollbacks_path = self.events_dir / "merge_rollbacks.jsonl"
        self.duplicate_scan_runs_path = self.events_dir / "duplicate_scan_runs.jsonl"
        self.duplicate_candidate_events_path = self.events_dir / "duplicate_candidate_events.jsonl"
        self.integrity_checks_path = self.events_dir / "integrity_checks.jsonl"

    def initialize_storage(self) -> None:
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.events_dir.mkdir(parents=True, exist_ok=True)
        for json_path in (
            self.golden_records_path,
            self.record_links_path,
            self.review_tasks_path,
            self.duplicate_candidates_path,
        ):
            if not json_path.exists():
                json_path.write_text("[]\n", encoding="utf-8")
        if not self.demo_manifest_path.exists():
            self.demo_manifest_path.write_text("{}\n", encoding="utf-8")
        for jsonl_path in (
            self.source_records_path,
            self.match_runs_path,
            self.merge_history_path,
            self.audit_log_path,
            self.merge_events_path,
            self.primary_overrides_path,
            self.merge_rollbacks_path,
            self.duplicate_scan_runs_path,
            self.duplicate_candidate_events_path,
            self.integrity_checks_path,
        ):
            if not jsonl_path.exists():
                jsonl_path.write_text("", encoding="utf-8")

    def validate_storage(self) -> None:
        self.initialize_storage()
        self._read_state_array(self.golden_records_path)
        self._read_state_array(self.record_links_path)
        self._read_state_array(self.review_tasks_path)
        self._read_state_array(self.duplicate_candidates_path)

    def _read_state_array(self, path: Path) -> list[dict[str, Any]]:
        try:
            content = path.read_text(encoding="utf-8")
            data = json.loads(content or "[]")
        except json.JSONDecodeError as exc:
            raise RepositoryError(f"corrupted JSON state file: {path}") from exc
        if not isinstance(data, list):
            raise RepositoryError(f"state file must contain JSON array: {path}")
        return data

    def _write_state_array_atomic(self, path: Path, rows: list[dict[str, Any]]) -> None:
        _ = self._read_state_array(path)

        serialized = json.dumps(rows, ensure_ascii=False, indent=2, sort_keys=True)
        try:
            json.loads(serialized)
        except json.JSONDecodeError as exc:
            raise RepositoryError(f"internal serialization error for state file: {path}") from exc

        backup_path = path.with_suffix(path.suffix + ".bak")
        shutil.copy2(path, backup_path)

        with NamedTemporaryFile("w", encoding="utf-8", delete=False, dir=path.parent) as tmp_file:
            tmp_file.write(serialized + "\n")
            temp_path = Path(tmp_file.name)
        temp_path.replace(path)

    def _read_state_object(self, path: Path) -> dict[str, Any]:
        try:
            content = path.read_text(encoding="utf-8")
            data = json.loads(content or "{}")
        except json.JSONDecodeError as exc:
            raise RepositoryError(f"corrupted JSON state file: {path}") from exc
        if not isinstance(data, dict):
            raise RepositoryError(f"state file must contain JSON object: {path}")
        return data

    def _write_state_object_atomic(self, path: Path, payload: dict[str, Any]) -> None:
        _ = self._read_state_object(path)
        serialized = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)
        try:
            json.loads(serialized)
        except json.JSONDecodeError as exc:
            raise RepositoryError(f"internal serialization error for state file: {path}") from exc
        backup_path = path.with_suffix(path.suffix + ".bak")
        shutil.copy2(path, backup_path)
        with NamedTemporaryFile("w", encoding="utf-8", delete=False, dir=path.parent) as tmp_file:
            tmp_file.write(serialized + "\n")
            temp_path = Path(tmp_file.name)
        temp_path.replace(path)

    def _append_jsonl(self, path: Path, payload: dict[str, Any]) -> None:
        line = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        with path.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    def _load_typed_state(self, path: Path, factory: Callable[[dict[str, Any]], Any]) -> list[Any]:
        return [factory(item) for item in self._read_state_array(path)]

    def _load_typed_jsonl(self, path: Path, factory: Callable[[dict[str, Any]], Any]) -> list[Any]:
        if not path.exists():
            return []
        rows: list[Any] = []
        with path.open("r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(factory(json.loads(line)))
                except json.JSONDecodeError as exc:
                    raise RepositoryError(f"corrupted JSONL event file: {path}") from exc
        return rows

    def append_source_record(self, record: SourceRecord) -> None:
        existing = self.find_source_record_by_payload_hash(
            source_system=record.source_system,
            source_pk=record.source_pk,
            payload_hash=record.payload_hash,
        )
        if existing is not None:
            raise DuplicateSourceRecordError(
                "duplicate source record for source_system+source_pk+payload_hash"
            )
        self._append_jsonl(self.source_records_path, record.to_dict())

    def load_source_records(self) -> list[SourceRecord]:
        return self._load_typed_jsonl(self.source_records_path, SourceRecord.from_dict)

    def find_source_record(self, source_record_id: str) -> SourceRecord | None:
        for record in self.load_source_records():
            if record.source_record_id == source_record_id:
                return record
        return None

    def find_source_records_by_external_key(self, source_system: str, source_pk: str) -> list[SourceRecord]:
        return [
            rec
            for rec in self.load_source_records()
            if rec.source_system == source_system and rec.source_pk == source_pk
        ]

    def find_source_record_by_payload_hash(
        self, source_system: str, source_pk: str, payload_hash: str
    ) -> SourceRecord | None:
        for rec in self.find_source_records_by_external_key(source_system, source_pk):
            if rec.payload_hash == payload_hash:
                return rec
        return None

    def load_golden_records(self) -> list[GoldenRecord]:
        return self._load_typed_state(self.golden_records_path, GoldenRecord.from_dict)

    def find_golden_record(self, golden_record_id: str) -> GoldenRecord | None:
        for rec in self.load_golden_records():
            if rec.golden_record_id == golden_record_id:
                return rec
        return None

    def save_golden_records(self, records: list[GoldenRecord]) -> None:
        self._write_state_array_atomic(self.golden_records_path, [item.to_dict() for item in records])

    def load_record_links(self) -> list[RecordLink]:
        return self._load_typed_state(self.record_links_path, RecordLink.from_dict)

    def save_record_links(self, links: list[RecordLink]) -> None:
        self._write_state_array_atomic(self.record_links_path, [item.to_dict() for item in links])

    def append_match_run(self, run: MatchRun) -> None:
        self._append_jsonl(self.match_runs_path, run.to_dict())

    def load_match_runs(self) -> list[MatchRun]:
        return self._load_typed_jsonl(self.match_runs_path, MatchRun.from_dict)

    def find_match_run(self, run_id: str) -> MatchRun | None:
        for run in self.load_match_runs():
            if run.run_id == run_id:
                return run
        return None

    def load_merge_history_events(self) -> list[MergeHistoryEvent]:
        return self._load_typed_jsonl(self.merge_history_path, MergeHistoryEvent.from_dict)

    def load_audit_events(self) -> list[AuditEvent]:
        return self._load_typed_jsonl(self.audit_log_path, AuditEvent.from_dict)

    def load_manual_review_tasks(self) -> list[ManualReviewTask]:
        return self._load_typed_state(self.review_tasks_path, ManualReviewTask.from_dict)

    def save_manual_review_tasks(self, tasks: list[ManualReviewTask]) -> None:
        self._write_state_array_atomic(self.review_tasks_path, [item.to_dict() for item in tasks])

    def append_merge_history_event(self, event: MergeHistoryEvent) -> None:
        self._append_jsonl(self.merge_history_path, event.to_dict())

    def append_audit_event(self, event: AuditEvent) -> None:
        self._append_jsonl(self.audit_log_path, event.to_dict())

    # --- Phase 1.2: single-entity upserts -------------------------------

    def save_golden_record(self, record: GoldenRecord) -> None:
        records = self.load_golden_records()
        replaced = False
        updated: list[GoldenRecord] = []
        for existing in records:
            if existing.golden_record_id == record.golden_record_id:
                updated.append(record)
                replaced = True
            else:
                updated.append(existing)
        if not replaced:
            updated.append(record)
        self.save_golden_records(updated)

    def save_record_link(self, link: RecordLink) -> None:
        links = self.load_record_links()
        replaced = False
        updated: list[RecordLink] = []
        for existing in links:
            if existing.link_id == link.link_id:
                updated.append(link)
                replaced = True
            else:
                updated.append(existing)
        if not replaced:
            updated.append(link)
        self.save_record_links(updated)

    # --- Phase 1.2: merge / override / rollback event streams ------------

    def save_merge_event(self, event: MergeEvent) -> None:
        self._append_jsonl(self.merge_events_path, event.to_dict())

    def load_merge_events(self) -> list[MergeEvent]:
        return self._load_typed_jsonl(self.merge_events_path, MergeEvent.from_dict)

    def load_merge_event(self, merge_id: str) -> MergeEvent | None:
        for event in self.load_merge_events():
            if event.merge_id == merge_id:
                return event
        return None

    def save_primary_override_event(self, event: PrimaryOverrideEvent) -> None:
        self._append_jsonl(self.primary_overrides_path, event.to_dict())

    def load_primary_override_events(self) -> list[PrimaryOverrideEvent]:
        return self._load_typed_jsonl(self.primary_overrides_path, PrimaryOverrideEvent.from_dict)

    def save_rollback_event(self, event: RollbackEvent) -> None:
        self._append_jsonl(self.merge_rollbacks_path, event.to_dict())

    def load_rollback_events(self) -> list[RollbackEvent]:
        return self._load_typed_jsonl(self.merge_rollbacks_path, RollbackEvent.from_dict)

    def find_rollback_for_merge(self, merge_id: str) -> RollbackEvent | None:
        for event in self.load_rollback_events():
            if event.merge_id == merge_id:
                return event
        return None

    def load_duplicate_candidates(self) -> list[DuplicateCandidate]:
        return self._load_typed_state(self.duplicate_candidates_path, DuplicateCandidate.from_dict)

    def save_duplicate_candidates(self, candidates: list[DuplicateCandidate]) -> None:
        self._write_state_array_atomic(self.duplicate_candidates_path, [item.to_dict() for item in candidates])

    def append_duplicate_scan_run(self, scan_run: DuplicateScanRun) -> None:
        self._append_jsonl(self.duplicate_scan_runs_path, scan_run.to_dict())

    def load_duplicate_scan_runs(self) -> list[DuplicateScanRun]:
        return self._load_typed_jsonl(self.duplicate_scan_runs_path, DuplicateScanRun.from_dict)

    def append_duplicate_candidate_event(self, payload: dict) -> None:
        self._append_jsonl(self.duplicate_candidate_events_path, payload)

    def load_demo_manifest(self) -> DemoScenarioManifest | None:
        data = self._read_state_object(self.demo_manifest_path)
        if not data:
            return None
        return DemoScenarioManifest.from_dict(data)

    def save_demo_manifest(self, manifest: DemoScenarioManifest) -> None:
        self._write_state_object_atomic(self.demo_manifest_path, manifest.to_dict())

    def clear_demo_manifest(self) -> None:
        self._write_state_object_atomic(self.demo_manifest_path, {})

    def save_integrity_finding_event(self, finding: IntegrityFinding) -> None:
        self._append_jsonl(self.integrity_checks_path, finding.to_dict())

    # --- Phase 1.2: transactional snapshot/restore ----------------------

    _SNAPSHOT_ATTRS = (
        "golden_records_path",
        "record_links_path",
        "review_tasks_path",
        "merge_history_path",
        "audit_log_path",
        "merge_events_path",
        "primary_overrides_path",
        "merge_rollbacks_path",
        "duplicate_candidates_path",
        "demo_manifest_path",
        "duplicate_scan_runs_path",
        "duplicate_candidate_events_path",
        "integrity_checks_path",
    )

    @contextmanager
    def atomic_update(self) -> Iterator["JsonFileRepository"]:
        """Snapshot mutable storage files and restore them if the block raises.

        This gives multi-file writes all-or-nothing semantics for the
        single-writer Phase 1 storage model: any partially applied state or
        appended events are rolled back to the pre-transaction snapshot.
        """
        snapshots: dict[Path, str | None] = {}
        for attr in self._SNAPSHOT_ATTRS:
            path = getattr(self, attr)
            snapshots[path] = path.read_text(encoding="utf-8") if path.exists() else None
        try:
            yield self
        except Exception:
            for path, content in snapshots.items():
                if content is None:
                    if path.exists():
                        path.unlink()
                else:
                    path.write_text(content, encoding="utf-8")
            raise
