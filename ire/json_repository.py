from __future__ import annotations

import json
import shutil
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any, Callable

from .exceptions import DuplicateSourceRecordError, RepositoryError
from .models import AuditEvent, GoldenRecord, ManualReviewTask, MatchRun, MergeHistoryEvent, RecordLink, SourceRecord


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

        self.source_records_path = self.events_dir / "source_records.jsonl"
        self.match_runs_path = self.events_dir / "match_runs.jsonl"
        self.merge_history_path = self.events_dir / "merge_history.jsonl"
        self.audit_log_path = self.events_dir / "audit_log.jsonl"

    def initialize_storage(self) -> None:
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.events_dir.mkdir(parents=True, exist_ok=True)
        for json_path in (self.golden_records_path, self.record_links_path, self.review_tasks_path):
            if not json_path.exists():
                json_path.write_text("[]\n", encoding="utf-8")
        for jsonl_path in (
            self.source_records_path,
            self.match_runs_path,
            self.merge_history_path,
            self.audit_log_path,
        ):
            if not jsonl_path.exists():
                jsonl_path.write_text("", encoding="utf-8")

    def validate_storage(self) -> None:
        self.initialize_storage()
        self._read_state_array(self.golden_records_path)
        self._read_state_array(self.record_links_path)
        self._read_state_array(self.review_tasks_path)

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

    def _append_jsonl(self, path: Path, payload: dict[str, Any]) -> None:
        line = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        with path.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    def _load_typed_state(self, path: Path, factory: Callable[[dict[str, Any]], Any]) -> list[Any]:
        return [factory(item) for item in self._read_state_array(path)]

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
        if not self.source_records_path.exists():
            return []
        records: list[SourceRecord] = []
        with self.source_records_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    records.append(SourceRecord.from_dict(json.loads(line)))
                except json.JSONDecodeError as exc:
                    raise RepositoryError(
                        f"corrupted JSONL event file: {self.source_records_path}"
                    ) from exc
        return records

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
        if not self.match_runs_path.exists():
            return []
        runs: list[MatchRun] = []
        with self.match_runs_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    runs.append(MatchRun.from_dict(json.loads(line)))
                except json.JSONDecodeError as exc:
                    raise RepositoryError(f"corrupted JSONL event file: {self.match_runs_path}") from exc
        return runs

    def find_match_run(self, run_id: str) -> MatchRun | None:
        for run in self.load_match_runs():
            if run.run_id == run_id:
                return run
        return None

    def load_manual_review_tasks(self) -> list[ManualReviewTask]:
        return self._load_typed_state(self.review_tasks_path, ManualReviewTask.from_dict)

    def save_manual_review_tasks(self, tasks: list[ManualReviewTask]) -> None:
        self._write_state_array_atomic(self.review_tasks_path, [item.to_dict() for item in tasks])

    def append_merge_history_event(self, event: MergeHistoryEvent) -> None:
        self._append_jsonl(self.merge_history_path, event.to_dict())

    def append_audit_event(self, event: AuditEvent) -> None:
        self._append_jsonl(self.audit_log_path, event.to_dict())
