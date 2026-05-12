# IRE POC - Phase 1 (Blueprint Aligned)

This repository now contains a complete Phase 1 baseline aligned to the requested architecture:

- Unified API Gateway (`POST /api/v1/ingest`) for Event system, Attendance, and 3rd-party forms
- Waterfall matching engine (TIER-1 deterministic, TIER-2 probabilistic, TIER-3 review routing)
- Source credibility scoring (CRM 1.0, ADMS/Attendance 0.9, 3rd-party 0.7)
- Manual review workflow APIs (`/api/v1/reviews/*`)
- Identity stitching support (`IdentityGraph`)
- IAM-ready integration (JWT + verified identity lookup)
- Redis caching (identity, queue, source credibility)
- Oracle-ready schema and Flyway migrations
- Monitoring and observability (Actuator + custom metrics hooks)

## Key Endpoints

- `POST /api/v1/ingest`
- `GET /api/v1/ingest/status`
- `GET /api/v1/identities/{id}`
- `GET /api/v1/reviews`
- `GET /api/v1/reviews/{id}`
- `POST /api/v1/reviews/{id}/approve`
- `POST /api/v1/reviews/{id}/reject`
- `POST /api/v1/reviews/{id}/merge`
- `GET /api/v1/health`

## Sample Schemas

- `src/main/resources/schema/event-system-schema.json`
- `src/main/resources/schema/attendance-schema.json`
- `src/main/resources/schema/3rd-party-forms-schema.json`

## Run tests

```bash
mvn test
```
