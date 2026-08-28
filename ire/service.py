from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .candidate_generation import CandidateBlock, generate_candidates
from .config import IREConfig
from .decision import DecisionResult, make_decision
from .deterministic import check_deterministic
from .enums import LinkStatus, MatchDecisionType, MatchMethod, MatchTier, MergeEventType
from .evidence import build_evidence
from .ids import (
    new_audit_event_id,
    new_candidate_id,
    new_match_run_id,
    new_merge_event_id,
    new_record_link_id,
    utc_now_iso,
)
from .ingestion import ingest_record
from .models import AuditEvent, MatchCandidate, MatchFeature, MatchRun, MergeHistoryEvent, RecordLink
from .repository import IRERepository
from .review import create_review_task
from .safety import check_safety
from .scoring import compute_score
from .survivorship import apply_survivorship, create_golden_record


@dataclass
class ProcessResult:
    source_record_id: str | None
    outcome: str
    golden_record_id: str | None
    review_task_id: str | None
    is_duplicate: bool
    is_revision: bool
    validation_issues: list[dict] = field(default_factory=list)
    match_run_id: str | None = None
    confidence: float = 0.0
    reason: str = ""


class _PreviewRepository:
    def __init__(self, repo: IRERepository) -> None:
        self._source_records = list(repo.load_source_records())
        self._goldens = list(repo.load_golden_records())
        self._links = list(repo.load_record_links())
        self._tasks = list(repo.load_manual_review_tasks())
        self._runs = list(repo.load_match_runs())
        self._audits: list[AuditEvent] = []
        self._merge_history: list[MergeHistoryEvent] = []

    def initialize_storage(self) -> None:
        return None

    def validate_storage(self) -> None:
        return None

    def append_source_record(self, record):
        self._source_records.append(record)

    def find_source_records_by_external_key(self, source_system: str, source_pk: str):
        return [r for r in self._source_records if r.source_system == source_system and r.source_pk == source_pk]

    def find_source_record_by_payload_hash(self, source_system: str, source_pk: str, payload_hash: str):
        return next((r for r in self.find_source_records_by_external_key(source_system, source_pk) if r.payload_hash == payload_hash), None)

    def load_source_records(self):
        return list(self._source_records)

    def find_source_record(self, source_record_id: str):
        return next((r for r in self._source_records if r.source_record_id == source_record_id), None)

    def load_golden_records(self):
        return list(self._goldens)

    def find_golden_record(self, golden_record_id: str):
        return next((r for r in self._goldens if r.golden_record_id == golden_record_id), None)

    def save_golden_records(self, records):
        self._goldens = list(records)

    def load_record_links(self):
        return list(self._links)

    def save_record_links(self, links):
        self._links = list(links)

    def append_match_run(self, run):
        self._runs.append(run)

    def load_match_runs(self):
        return list(self._runs)

    def find_match_run(self, run_id: str):
        return next((r for r in self._runs if r.run_id == run_id), None)

    def load_manual_review_tasks(self):
        return list(self._tasks)

    def save_manual_review_tasks(self, tasks):
        self._tasks = list(tasks)

    def append_merge_history_event(self, event):
        self._merge_history.append(event)

    def append_audit_event(self, event):
        self._audits.append(event)


def _issues_to_dicts(issues) -> list[dict[str, Any]]:
    return [{"field": i.field, "code": i.code, "message": i.message, "severity": i.severity} for i in issues]


def _source_system(config: IREConfig, code: str):
    return next(system for system in config.source_systems if system.code == code)


def _candidate_features(scoring_result) -> list[MatchFeature]:
    return [
        MatchFeature(name=evidence.field, value=evidence.similarity, weight=evidence.normalized_weight, evidence=evidence.explanation)
        for evidence in scoring_result.evidence
        if evidence.is_comparable
    ]


def _tier_for_score(score: float) -> MatchTier:
    if score >= 0.99:
        return MatchTier.EXACT
    if score >= 0.85:
        return MatchTier.STRONG
    return MatchTier.WEAK


def _build_match_run(source_record_id: str, scored_candidates, decision: DecisionResult, config: IREConfig) -> MatchRun:
    candidates: list[MatchCandidate] = []
    ordered = sorted(scored_candidates, key=lambda item: item[1].final_score, reverse=True)
    best_candidate_id: str | None = None
    for index, (candidate_block, scoring_result) in enumerate(ordered):
        candidate_id = new_candidate_id()
        if index == 0:
            best_candidate_id = candidate_id
        candidates.append(
            MatchCandidate(
                candidate_id=candidate_id,
                golden_record_id=candidate_block.golden_record_id,
                score=scoring_result.final_score,
                method=MatchMethod.PROBABILISTIC,
                tier=_tier_for_score(scoring_result.final_score),
                features=_candidate_features(scoring_result),
                safety_flags=[],
                explainability={
                    "blocking_reasons": candidate_block.blocking_reasons,
                    "adjustments": scoring_result.adjustments,
                    "evidence": [e.__dict__ for e in scoring_result.evidence],
                },
            )
        )
    return MatchRun(
        run_id=new_match_run_id(),
        source_record_id=source_record_id,
        candidates=candidates,
        best_candidate_id=best_candidate_id,
        decision=MatchDecisionType(decision.outcome),
        policy_version=config.matching_policy.version,
        created_at=utc_now_iso(),
    )


def _persist_auto_merge(repo: IRERepository, config: IREConfig, source_record, normalized, golden_record_id: str, confidence: float, reason: str) -> None:
    golden = repo.find_golden_record(golden_record_id)
    if golden is None:
        raise ValueError(f"golden record not found: {golden_record_id}")
    updated = apply_survivorship(golden, normalized, source_record, _source_system(config, source_record.source_system), config)
    repo.save_golden_records([updated if item.golden_record_id == updated.golden_record_id else item for item in repo.load_golden_records()])
    links = repo.load_record_links()
    if not any(link.source_record_id == source_record.source_record_id and link.golden_record_id == golden_record_id and link.status == LinkStatus.ACTIVE for link in links):
        links.append(RecordLink(new_record_link_id(), source_record.source_record_id, golden_record_id, LinkStatus.ACTIVE, confidence, utc_now_iso(), utc_now_iso()))
        repo.save_record_links(links)
    repo.append_audit_event(AuditEvent(new_audit_event_id(), "AUTO_MERGE", "SourceRecord", source_record.source_record_id, "system", {"golden_record_id": golden_record_id, "reason": reason}, utc_now_iso()))


def _persist_create_golden(repo: IRERepository, config: IREConfig, source_record, normalized, confidence: float, reason: str) -> str:
    golden = create_golden_record(normalized, source_record, _source_system(config, source_record.source_system), config)
    goldens = repo.load_golden_records()
    goldens.append(golden)
    repo.save_golden_records(goldens)
    links = repo.load_record_links()
    links.append(RecordLink(new_record_link_id(), source_record.source_record_id, golden.golden_record_id, LinkStatus.ACTIVE, confidence or 1.0, utc_now_iso(), utc_now_iso()))
    repo.save_record_links(links)
    repo.append_merge_history_event(MergeHistoryEvent(new_merge_event_id(), MergeEventType.CREATE_GOLDEN, golden.golden_record_id, golden.golden_record_id, reason, utc_now_iso()))
    repo.append_audit_event(AuditEvent(new_audit_event_id(), "CREATE_GOLDEN", "GoldenRecord", golden.golden_record_id, "system", {"source_record_id": source_record.source_record_id, "reason": reason}, utc_now_iso()))
    return golden.golden_record_id


def process_record(raw: dict, config: IREConfig, repo: IRERepository) -> ProcessResult:
    ingest_result = ingest_record(raw, config, repo)
    validation = ingest_result.validation_result

    if ingest_result.is_duplicate and ingest_result.source_record is not None:
        return ProcessResult(
            source_record_id=ingest_result.source_record.source_record_id,
            outcome="DUPLICATE",
            golden_record_id=None,
            review_task_id=None,
            is_duplicate=True,
            is_revision=False,
            validation_issues=_issues_to_dicts(validation.issues),
            confidence=1.0,
            reason="duplicate payload already ingested",
        )

    if not validation.valid or ingest_result.source_record is None:
        return ProcessResult(
            source_record_id=None,
            outcome=MatchDecisionType.VALIDATION_FAILED.value,
            golden_record_id=None,
            review_task_id=None,
            is_duplicate=False,
            is_revision=False,
            validation_issues=_issues_to_dicts(validation.issues),
            confidence=0.0,
            reason="validation failed",
        )

    source_record = ingest_result.source_record
    source_system = _source_system(config, source_record.source_system)
    goldens = repo.load_golden_records()
    candidates = generate_candidates(validation.normalized, goldens, config)
    links = repo.load_record_links()
    deterministic = check_deterministic(validation.normalized, candidates, goldens, links, source_system, config)

    scored_candidates: list[tuple[CandidateBlock, Any]] = []
    goldens_by_id = {golden.golden_record_id: golden for golden in goldens}
    for candidate in candidates:
        golden = goldens_by_id.get(candidate.golden_record_id)
        if golden is None:
            continue
        evidence = build_evidence(validation.normalized, golden.fields, config)
        scored_candidates.append((candidate, compute_score(evidence, source_system, config)))

    safety = check_safety(deterministic, candidates, scored_candidates, source_system, source_record, links, config)
    decision = make_decision(validation, deterministic, candidates, scored_candidates, safety, config)

    run = _build_match_run(source_record.source_record_id, scored_candidates, decision, config)
    repo.append_match_run(run)

    review_task_id: str | None = None
    golden_record_id = decision.golden_record_id
    if decision.outcome == MatchDecisionType.AUTO_MERGE.value and golden_record_id is not None:
        _persist_auto_merge(repo, config, source_record, validation.normalized, golden_record_id, decision.confidence, decision.reason)
    elif decision.outcome == MatchDecisionType.CREATE_NEW_GOLDEN.value:
        golden_record_id = _persist_create_golden(repo, config, source_record, validation.normalized, decision.confidence, decision.reason)
    elif decision.outcome == MatchDecisionType.MANUAL_REVIEW.value:
        review_task = create_review_task(source_record, run.run_id, [candidate.candidate_id for candidate in run.candidates], decision.safety_flags, repo)
        review_task_id = review_task.review_id

    return ProcessResult(
        source_record_id=source_record.source_record_id,
        outcome=decision.outcome,
        golden_record_id=golden_record_id,
        review_task_id=review_task_id,
        is_duplicate=False,
        is_revision=ingest_result.is_revision,
        validation_issues=_issues_to_dicts(validation.issues),
        match_run_id=run.run_id,
        confidence=decision.confidence,
        reason=decision.reason,
    )


def process_batch(records: list[dict], config: IREConfig, repo: IRERepository) -> list[ProcessResult]:
    results: list[ProcessResult] = []
    for record in records:
        try:
            results.append(process_record(record, config, repo))
        except Exception as exc:
            results.append(ProcessResult(None, MatchDecisionType.VALIDATION_FAILED.value, None, None, False, False, [{"field": "record", "code": "PROCESS_ERROR", "message": str(exc), "severity": "ERROR"}], None, 0.0, "processing failed"))
    return results


def preview_record(raw: dict, config: IREConfig, repo: IRERepository) -> dict:
    preview_repo = _PreviewRepository(repo)
    result = process_record(raw, config, preview_repo)
    return {
        "source_record_id": result.source_record_id,
        "outcome": result.outcome,
        "golden_record_id": result.golden_record_id,
        "review_task_id": result.review_task_id,
        "is_duplicate": result.is_duplicate,
        "is_revision": result.is_revision,
        "validation_issues": result.validation_issues,
        "match_run_id": result.match_run_id,
        "confidence": result.confidence,
        "reason": result.reason,
    }
