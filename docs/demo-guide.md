# Phase 1.3 FastAPI Demo Guide

## What this demo is
A local-only FastAPI + Jinja2 demonstration layer for the Python Phase 1 Identity Resolution Engine.

## What this demo is not
- Not a production service
- No authentication
- No database
- No Phase 2 relationship/graph functionality

## Install
```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
```

## Start the demo
```bash
python -m ire web --host 127.0.0.1 --port 8000 --root data/demo-run --config-dir config
```

Optional:
```bash
uvicorn ire.web.app:create_app --factory --host 127.0.0.1 --port 8000
```

## URLs
- Dashboard: `http://127.0.0.1:8000/`
- OpenAPI: `http://127.0.0.1:8000/docs`

## Demo workflow
Recommended deterministic setup:
```bash
python -m ire demo reset --root data/demo-run --yes
python -m ire demo seed --scenario full-showcase --root data/demo-run
python -m ire demo status --root data/demo-run
```

Then walk through:
1. Submit an identity at `/identities/new`
2. Upload a CSV or JSON batch at `/identities/batch`
3. Inspect Golden Records at `/golden-records`
4. Review manual tasks at `/reviews`
5. Review potential duplicate queue at `/duplicates`
6. Check storage integrity report at `/integrity`
7. Show data quality metrics and source drilldown at `/data-quality`
8. Export masked evidence from `/api/v1/exports/{dataset}?format=csv|json`

## API example
```bash
curl -X POST http://127.0.0.1:8000/api/v1/identities/process \
  -H 'content-type: application/json' \
  -d '{
    "source_system": "SIS",
    "source_pk": "SIS-10001",
    "data": {
      "emplid": "100001",
      "first_name": "Siu Mei",
      "last_name": "Chan",
      "email": "smchan@example.edu",
      "phone": "8567-4123",
      "date_of_birth": "1985-05-20"
    }
  }'
```

## CSV format
```csv
source_system,source_pk,emplid,first_name,last_name,email,phone,date_of_birth,gender,address
SIS,SIS-10001,100001,Siu Mei,Chan,smchan@example.edu,8567-4123,1985-05-20,F,"1 Demo Street"
```

## Isolated storage roots
Use a dedicated root per demo/test run:
```bash
python -m ire storage init --root data/demo-run
python -m ire web --root data/demo-run
```

## Reset demo state
```bash
python -m ire demo reset --root data/demo-run --yes
```

## Limitations
- JSON/JSONL file storage only
- Single-writer assumption
- Manual review field overrides are not supported by the current Phase 1 MVP service
- Demo masking is presentation-only; it does not alter stored matching state
- Synthetic data only
- Localhost binding required because there is no authentication layer
