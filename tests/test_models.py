from ire.enums import (
    GoldenRecordStatus,
    IngestStatus,
    LinkStatus,
    MatchDecisionType,
    MatchMethod,
    MatchTier,
    MergeEventType,
    ReviewDecisionType,
    ReviewStatus,
    SafetyFlag,
    SourceTrustLevel,
)
from ire.models import (
    AuditEvent,
    GoldenFieldValue,
    GoldenRecord,
    ManualReviewDecision,
    ManualReviewTask,
    MatchCandidate,
    MatchDecision,
    MatchFeature,
    MatchRun,
    MergeHistoryEvent,
    NormalizedIdentity,
    RecordLink,
    SourceRecord,
    SourceSystem,
)

TS = "2026-01-01T00:00:00Z"


def test_model_roundtrip_serialization() -> None:
    source_system = SourceSystem("SIS", "SIS", SourceTrustLevel.HIGH, 0.9, True, True)
    source_record = SourceRecord("SRC-1", "SIS", "123", {"name": "A"}, "h1", TS, IngestStatus.VALIDATED)
    normalized = NormalizedIdentity(full_name="Alice")
    field_value = GoldenFieldValue("Alice", "alice", "SRC-1", "SIS", 0.9, True, True, False, True, TS)
    golden = GoldenRecord("GR-1", GoldenRecordStatus.ACTIVE, {"name": [field_value]}, TS, TS)
    link = RecordLink("LINK-1", "SRC-1", "GR-1", LinkStatus.ACTIVE, 0.91, TS, TS)
    feature = MatchFeature("name_similarity", 0.95, 1.0)
    candidate = MatchCandidate(
        "CAND-1",
        "GR-1",
        0.92,
        MatchMethod.DETERMINISTIC,
        MatchTier.EXACT,
        [feature],
        [SafetyFlag.LOW_EVIDENCE],
    )
    run = MatchRun("RUN-1", "SRC-1", [candidate], "CAND-1", MatchDecisionType.AUTO_MERGE, "1.0.0", TS)
    decision = MatchDecision("DEC-1", "RUN-1", MatchDecisionType.AUTO_MERGE, MatchTier.EXACT, 0.92, "reason", TS)
    review_task = ManualReviewTask("REV-1", "RUN-1", "SRC-1", ["CAND-1"], ReviewStatus.OPEN, TS, TS)
    review_decision = ManualReviewDecision("RDEC-1", "REV-1", ReviewDecisionType.APPROVE_MERGE, "reviewer", "ok", TS)
    merge = MergeHistoryEvent("MERGE-1", MergeEventType.MANUAL, "GR-1", "GR-2", "manual merge", TS, "RUN-1")
    audit = AuditEvent("AUD-1", "CREATE", "GoldenRecord", "GR-1", "system", {"k": "v"}, TS)

    assert SourceSystem.from_dict(source_system.to_dict()) == source_system
    assert SourceRecord.from_dict(source_record.to_dict()) == source_record
    assert NormalizedIdentity.from_dict(normalized.to_dict()) == normalized
    assert GoldenFieldValue.from_dict(field_value.to_dict()) == field_value
    assert GoldenRecord.from_dict(golden.to_dict()) == golden
    assert RecordLink.from_dict(link.to_dict()) == link
    assert MatchFeature.from_dict(feature.to_dict()) == feature
    assert MatchCandidate.from_dict(candidate.to_dict()) == candidate
    assert MatchRun.from_dict(run.to_dict()) == run
    assert MatchDecision.from_dict(decision.to_dict()) == decision
    assert ManualReviewTask.from_dict(review_task.to_dict()) == review_task
    assert ManualReviewDecision.from_dict(review_decision.to_dict()) == review_decision
    assert MergeHistoryEvent.from_dict(merge.to_dict()) == merge
    assert AuditEvent.from_dict(audit.to_dict()) == audit
