from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from ire.web.dependencies import WebRuntime, get_runtime

router = APIRouter(tags=["history"])


def _templates(request):
    return request.app.state.templates


def _details_summary(details) -> str | None:
    if not isinstance(details, dict):
        return None
    safe_parts: list[str] = []
    for key in ("reason", "source_system", "source_pk", "action", "run_id", "is_revision", "golden_record_id", "source_record_id"):
        value = details.get(key)
        if value not in (None, "", []):
            safe_parts.append(f"{key.replace('_', ' ')}: {value}")
    issues = details.get("issues")
    if isinstance(issues, list):
        safe_parts.append(f"issues: {len(issues)}")
    return "; ".join(safe_parts) if safe_parts else None


def _result_summary(details) -> str | None:
    if not isinstance(details, dict):
        return None
    for key in ("golden_record_id", "action", "source_record_id", "run_id", "source_system"):
        value = details.get(key)
        if value not in (None, "", []):
            return str(value)
    issues = details.get("issues")
    if isinstance(issues, list):
        return f"{len(issues)} issue(s)"
    return None


@router.get("/match-history")
def match_history(request: Request, q: str | None = Query(default=None), decision: str | None = Query(default=None), runtime: WebRuntime = Depends(get_runtime)):
    runs = runtime.repo.load_match_runs()
    source_records = {r.source_record_id: r for r in runtime.repo.load_source_records()}
    rows = []
    for run in sorted(runs, key=lambda r: r.created_at, reverse=True):
        sr = source_records.get(run.source_record_id)
        best = next((c for c in run.candidates if c.candidate_id == run.best_candidate_id), run.candidates[0] if run.candidates else None)
        safety_flags = [flag.value for flag in best.safety_flags] if best else []
        rows.append({
            "run_id": run.run_id,
            "created_at": run.created_at,
            "source_system": sr.source_system if sr else None,
            "source_pk": sr.source_pk if sr else None,
            "decision": run.decision.value,
            "confidence": best.score if best else 0.0,
            "golden_record_id": best.golden_record_id if best else None,
            "method": best.method if best else None,
            "tier": best.tier if best else None,
            "safety_flags": safety_flags,
        })
    if q:
        q_lower = q.lower()
        rows = [r for r in rows if q_lower in r["run_id"].lower() or q_lower in (r["source_pk"] or "").lower() or q_lower in (r["source_system"] or "").lower()]
    if decision:
        rows = [r for r in rows if r["decision"].upper() == decision.upper()]
    return _templates(request).TemplateResponse(request, "match_history.html", {"runs": rows, "filters": {"q": q or "", "decision": decision or ""}, "result_count": len(rows), "current_path": request.url.path})


@router.get("/activity")
def activity_log(request: Request, event_type: str | None = Query(default=None), q: str | None = Query(default=None), runtime: WebRuntime = Depends(get_runtime)):
    audits = runtime.repo.load_audit_events()
    merge_history = runtime.repo.load_merge_history_events()
    events = []
    for a in audits:
        events.append({
            "timestamp": a.created_at,
            "event_type": a.event_type,
            "entity_type": a.entity_type,
            "entity": a.entity_id,
            "actor": a.actor,
            "result": _result_summary(a.details),
            "details": _details_summary(a.details),
            "source": "audit",
        })
    for m in merge_history:
        events.append({
            "timestamp": m.created_at,
            "event_type": m.event_type.value,
            "entity_type": "GoldenRecord",
            "entity": m.winner_golden_record_id,
            "actor": "system",
            "result": m.loser_golden_record_id,
            "details": m.reason,
            "source": "merge",
        })
    events.sort(key=lambda e: e["timestamp"] or "", reverse=True)
    event_types = sorted(set(e["event_type"] for e in events if e["event_type"]))
    if event_type:
        events = [e for e in events if e["event_type"] == event_type]
    if q:
        q_lower = q.lower()
        events = [
            e
            for e in events
            if q_lower in (e.get("entity") or "").lower()
            or q_lower in (e.get("event_type") or "").lower()
            or q_lower in (e.get("entity_type") or "").lower()
        ]
    return _templates(request).TemplateResponse(request, "activity.html", {"events": events, "filters": {"event_type": event_type or "", "q": q or ""}, "event_types": event_types, "result_count": len(events), "current_path": request.url.path})
