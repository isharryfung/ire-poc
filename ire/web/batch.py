from __future__ import annotations

import csv
import io
import json

from fastapi import HTTPException


def parse_batch_upload(filename: str | None, content_type: str | None, payload: bytes) -> list[dict]:
    suffix = (filename or "").lower()
    try:
        text = payload.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=400, detail="Upload must be valid UTF-8 CSV or JSON") from exc
    if suffix.endswith(".csv"):
        reader = csv.DictReader(io.StringIO(text))
        rows = []
        for row in reader:
            source_system = (row.pop("source_system", "") or "").strip()
            source_pk = (row.pop("source_pk", "") or "").strip()
            data = {key: value for key, value in row.items() if value not in (None, "")}
            rows.append({"source_system": source_system, "source_pk": source_pk, "data": data})
        return rows
    if suffix.endswith(".json") or content_type == "application/json":
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=400, detail="Upload contains invalid JSON") from exc
        if isinstance(parsed, list):
            return parsed
        if isinstance(parsed, dict):
            return [parsed]
        raise HTTPException(status_code=400, detail="JSON upload must be an object or array")
    raise HTTPException(status_code=400, detail="Upload a CSV or JSON batch file")
