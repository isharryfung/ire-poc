# Phase 1 Replan: Blueprint-Aligned Architecture

## Overview

This document outlines the restructured Phase 1 implementation based on your exact architectural blueprint. The focus is on building a production-ready IRE (Identity Resolution Engine) middleware that aligns with HKUST's systems integration needs.

## Architecture Alignment

### Your Blueprint Components

```
HKUST Systems          API Gateway          IRE Middleware          Core Systems
(Sources)              (Ingestion)          (Matching Engine)        (Master Data)

Event system    ──┐
Attendance      ──┼──> API Gateway ────> IRE Middleware ────> CRM/Alumni DB
3rd-party       ──┘    (Unified Ingestion) (Waterfall Matching)  (Master Data)
forms                  (Dynamic JSON)      (Confidence Scoring)
                                          (Manual Review)        IAM (Midpoint)
                                          (Source Credibility)   (Verified IDs)
                                          
                                          └──> CRM Inbox
                                               (Manual Review)
```

## Phase 1: Foundation + API Gateway + Matching Engine

### Phase 1 Deliverables (Complete Replan)

#### 1. API Gateway Layer (NEW)
**Purpose:** Unified ingestion from HKUST systems (Event system, Attendance, 3rd-party forms)

Components:
- `ApiGatewayController.java` - Unified ingestion endpoint
- `DynamicPayloadParser.java` - Parse flexible JSON payloads
- `SourceSystemMapper.java` - Map different source formats to canonical form
- `PayloadValidator.java` - Validate incoming data
- Schema definitions for each source system

**Features:**
- Single `/api/v1/ingest` endpoint
- Dynamic JSON field mapping
- Source system identification
- Payload validation
- Error handling & logging

#### 2. IRE Middleware Layer (ENHANCED)
**Purpose:** Core matching engine with waterfall logic and confidence scoring

Components:
- `WaterfallMatchingEngine.java` - Multi-tier matching (TIER-1, 2, 3)
- `SourceCredibilityScorer.java` - Weight sources by reliability
- `ConfidenceCalculator.java` - Advanced confidence scoring
- `IdentityStitching.java` - Link related identities
- `ManualReviewWorkflow.java` - Route uncertain matches for review

**Features:**
- Waterfall matching logic
- Source credibility multipliers (CRM > Attendance > 3rd-party)
- Composite confidence scoring
- Identity graph relationships
- >95% auto-merge, <95% manual review, <50% create new

#### 3. Golden Record Management (ENHANCED)
**Purpose:** Central master data store (CRM/Alumni DB equivalent)

Components:
- Enhanced `Identity.java` with additional fields
- `IdentityGraph.java` - Relationships between identities
- `SourceCredibility.java` - Track source trustworthiness
- `ManualReview.java` - Track review queue
- Database schema updates

**Features:**
- Master data with full audit trail
- Multi-source linking with credibility scores
- Relationship tracking (student, staff, alumni)
- Review workflow status
- Data lineage tracking

#### 4. Manual Review System (NEW)
**Purpose:** CRM Inbox equivalent for human verification

Components:
- `ManualReviewService.java` - Review queue management
- `ManualReviewController.java` - Admin API endpoints
- `ReviewTask.java` - Individual review task entity
- `ReviewDecision.java` - Decision recording

**Features:**
- Review task queue
- Admin dashboard API
- Decision recording (approve, reject, merge)
- Merge conflict resolution
- Audit logging

#### 5. Security & Compliance (ENHANCED)
**Purpose:** PDPO + PCI compliance, IAM integration ready

Components:
- Enhanced `SecurityConfig.java`
- `AuditingAspect.java` - Cross-cutting audit logging
- `DataEncryption.java` - Field-level encryption
- `IamIntegrationReady.java` - Placeholder for Midpoint

**Features:**
- PDPO-compliant encryption
- Comprehensive audit trails
- Access control
- Data masking
- IAM integration hooks

#### 6. Monitoring & Observability (NEW)
**Purpose:** Track system health and performance

Components:
- `MetricsService.java` - Custom metrics
- `HealthCheckController.java` - Detailed health checks
- `PerformanceMonitor.java` - Latency/throughput tracking

**Features:**
- Real-time metrics
- Performance dashboards (Prometheus-ready)
- Alerting hooks
- System health status

---

## Detailed Component Breakdown

### 1. API Gateway Layer

#### Request Format (Unified)
```json
{
  "source_system": "EVENT_SYSTEM|ATTENDANCE|3RD_PARTY",
  "source_record_id": "unique_id_in_source",
  "timestamp": "2026-05-12T12:00:00Z",
  "payload": {
    // Flexible schema - depends on source
    "email": "john.doe@hkust.edu.hk",
    "name": "John Doe",
    "student_id": "20123456",
    "phone": "+852 1234 5678",
    "hkid": "A123456789"
  }
}
```

#### Key Classes
```
ApiGatewayController
├── POST /api/v1/ingest          # Unified ingestion
├── GET /api/v1/ingest/status    # Ingestion status
└── GET /api/v1/ingest/health    # Health check

DynamicPayloadParser
├── parseEventSystemPayload()
├── parseAttendancePayload()
├── parse3rdPartyPayload()
└── toCanonicalForm()

SourceSystemMapper
├── EVENT_SYSTEM -> CanonicalIdentity
├── ATTENDANCE -> CanonicalIdentity
└── 3RD_PARTY -> CanonicalIdentity

PayloadValidator
├── validateRequiredFields()
├── validateEmailFormat()
├── validateHkidFormat()
└── validatePhoneFormat()
```

### 2. Waterfall Matching Engine

#### Matching Flow
```
TIER-1: Deterministic (100% confidence)
├── HKID exact match
├── Staff/Student ID exact match
└── Email + Name exact match

        ↓ (No match)

TIER-2: Probabilistic (90-99% confidence)
├── Email + Fuzzy Name (≥95% similarity)
├── Mobile + Name fuzzy match
└── Composite scoring

        ↓ (No match or 50-90%)

TIER-3: Manual Review
├── Route to review queue
├── Set confidence score
└── Flag for admin approval

        ↓ (Decision)

Actions:
├── >95% Confidence: Auto-merge
├── 50-95% Confidence: Manual review
└── <50% Confidence: Create new record
```

#### Key Classes
```
WaterfallMatchingEngine
├── tier1Match(request)          # Deterministic
├── tier2Match(request)          # Probabilistic
├── tier3Route(request)          # Manual review
└── determineAction(confidence)

SourceCredibilityScorer
├── CRM: 1.0 (100% trusted)
├── ADMS/Attendance: 0.9 (90% trusted)
└── 3rd-party: 0.7 (70% trusted)

ConfidenceCalculator
├── baseConfidence()             # Match score
├── credibilityMultiplier()      # Source weight
├── sourceAgreement()            # Multiple sources
└── finalConfidence()            # Composite score

IdentityStitching
├── createRelationship()         # Link identities
├── updateGraph()                # Maintain graph
└── getRelatedIdentities()       # Traverse graph
```

### 3. Golden Record + Manual Review

#### Database Schema (Enhanced)

**IDENTITIES**
- Core fields (email, name, HKID, IDs)
- Match metadata (confidence, tier, matched_fields)
- Source credibility scores
- Relationship pointers (identity_graph)
- Review status (requires_review, review_status)

**IDENTITY_LINKS**
- identity_id (FK to IDENTITIES)
- source_system (EVENT, ATTENDANCE, CRM, 3RD_PARTY)
- source_record_id
- match_confidence
- credibility_score
- is_primary

**IDENTITY_GRAPH**
- parent_identity_id
- child_identity_id
- relationship_type (DUPLICATE, VARIANT, RELATED)
- confidence
- created_at

**MANUAL_REVIEWS**
- review_id (PK)
- identity_id (FK)
- status (PENDING, APPROVED, REJECTED, MERGED)
- confidence_score
- conflicting_data (JSON)
- reviewer_id
- decision_timestamp
- notes

**AUDIT_LOGS**
- operation (CREATE, MATCH, MERGE, REVIEW, DELETE)
- identity_id
- source_system
- details (JSON)
- confidence_score
- performer
- ip_address
- timestamp

### 4. Manual Review Workflow

#### Workflow
```
Match Request
    ↓
Waterfall Matching
    ├─ Confidence ≥95% → Auto-merge ✅
    ├─ 50-95% → Review Queue
    └─ <50% → Create new + Flag review

Review Queue
    ├─ Admin reviews conflicting data
    ├─ Decision options:
    │  ├─ APPROVE (merge identities)
    │  ├─ REJECT (create new)
    │  └─ MERGE_SPECIFIC (custom merge)
    └─ Log decision + audit trail
```

#### Key Classes
```
ManualReviewService
├── queueForReview(identity)
├── getReviewQueue()
├── approveMatch(review_id)
├── rejectMatch(review_id)
└── mergeIdentities(review_id, merge_rules)

ManualReviewController
├── GET /api/v1/reviews           # Get queue
├── GET /api/v1/reviews/{id}      # Get details
├── POST /api/v1/reviews/{id}/approve
├── POST /api/v1/reviews/{id}/reject
└── POST /api/v1/reviews/{id}/merge

ReviewTask
├── review_id
├── identity_a_id
├── identity_b_id
├── confidence_score
├── conflicting_fields
├── status
└── decision_notes
```

---

## Phase 1 File Structure (Revised)

```
ire-poc/
├── src/main/java/com/university/ire/
│   ├── IreApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── EncryptionConfig.java
│   │   └── AuditorAwareConfig.java
│   │
│   ├── controller/
│   │   ├── ApiGatewayController.java          # NEW - Unified ingestion
│   │   ├── IdentityController.java             # ENHANCED
│   │   ├── ManualReviewController.java         # NEW - Review queue
│   │   └── HealthCheckController.java          # NEW - Monitoring
│   │
│   ├── service/
│   │   ├── gateway/
│   │   │   ├── ApiGatewayService.java          # NEW - Unified ingestion
│   │   │   ├── DynamicPayloadParser.java       # NEW - Parse flexible JSON
│   │   │   ├── SourceSystemMapper.java         # NEW - Map to canonical
│   │   │   └── PayloadValidator.java           # NEW - Validation
│   │   │
│   │   ├── matching/
│   │   │   ├── WaterfallMatchingEngine.java    # NEW - Waterfall logic
│   │   │   ├── SourceCredibilityScorer.java    # NEW - Source weights
│   │   │   ├── ConfidenceCalculator.java       # NEW - Confidence scoring
│   │   │   ├── IdentityStitching.java          # NEW - Identity graph
│   │   │   └── MatchingEngineService.java      # REFACTORED
│   │   │
│   │   ├── review/
│   │   │   ├── ManualReviewService.java        # NEW - Review workflow
│   │   │   └── ReviewQueueManager.java         # NEW - Queue management
│   │   │
│   │   ├── identity/
│   │   │   ├── IdentityResolutionService.java  # ENHANCED
│   │   │   ├── IdentityMergeService.java       # NEW - Merge logic
│   │   │   └── IdentityGraphService.java       # NEW - Graph management
│   │   │
│   │   └── monitoring/
���   │       ├── MetricsService.java             # NEW - Custom metrics
│   │       └── PerformanceMonitor.java         # NEW - Latency tracking
│   │
│   ├── entity/
│   │   ├── Identity.java                       # ENHANCED
│   │   ├── IdentityLink.java
│   │   ├── IdentityGraph.java                  # NEW
│   │   ├── AuditLog.java                       # ENHANCED
│   │   ├── ManualReview.java                   # NEW
│   │   └── SourceCredibility.java              # NEW
│   │
│   ├── dto/
│   │   ├── ApiGatewayRequest.java              # NEW
│   │   ├── ApiGatewayResponse.java             # NEW
│   │   ├── IdentityMatchRequest.java           # REFACTORED
│   │   ├── IdentityMatchResponse.java          # ENHANCED
│   │   ├── ManualReviewDto.java                # NEW
│   │   ├── CanonicalIdentity.java              # NEW
│   │   └── PayloadValidationError.java         # NEW
│   │
│   ├── repository/
│   │   ├── IdentityRepository.java
│   │   ├── IdentityLinkRepository.java
│   │   ├── IdentityGraphRepository.java        # NEW
│   │   ├── ManualReviewRepository.java         # NEW
│   │   ├── AuditLogRepository.java
│   │   └── SourceCredibilityRepository.java    # NEW
│   │
│   ├── aspect/
│   │   ├── AuditingAspect.java                 # NEW - Cross-cutting auditing
│   │   └── PerformanceAspect.java              # NEW - Performance tracking
│   │
│   └── exception/
│       ├── IdentityResolutionException.java    # NEW
│       ├── InvalidPayloadException.java        # NEW
│       └── GlobalExceptionHandler.java         # NEW
│
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/
│   │   ├── V1__init_identities.sql
│   │   ├── V2__init_identity_links.sql
│   │   ├── V3__init_identity_graph.sql         # NEW
│   │   ├── V4__init_manual_reviews.sql         # NEW
│   │   ├── V5__init_audit_logs.sql
│   │   └── V6__init_source_credibility.sql     # NEW
│   │
│   └── schema/
│       ├── event-system-schema.json            # NEW - Event system format
│       ├── attendance-schema.json              # NEW - Attendance format
│       └── 3rd-party-schema.json               # NEW - 3rd-party format
│
├── src/test/
│   ├── java/com/university/ire/
│   │   ├── controller/
│   │   │   ├── ApiGatewayControllerTest.java   # NEW
│   │   │   └── ManualReviewControllerTest.java # NEW
│   │   │
│   │   ├── service/
│   │   │   ├── WaterfallMatchingEngineTest.java # NEW
│   │   │   ├── SourceCredibilityScorerTest.java # NEW
│   │   │   ├── ConfidenceCalculatorTest.java   # NEW
│   │   │   ├── ApiGatewayServiceTest.java      # NEW
│   │   │   └── MatchingEngineServiceTest.java  # REFACTORED
│   │   │
│   │   └── integration/
│   │       └── EndToEndIdentityResolutionTest.java # NEW
│   │
│   └── resources/
│       └── test-data/
│           ├── event-system-payloads.json      # NEW
│           ├── attendance-payloads.json        # NEW
│           └── 3rd-party-payloads.json         # NEW
│
├── docker-compose.yml                          # ENHANCED (add Redis, etc.)
├── pom.xml                                     # UPDATED dependencies
├── Dockerfile                                  # UNCHANGED
├── README.md                                   # UPDATED
└── ARCHITECTURE.md                             # NEW - Detailed architecture

```

---

## Key Enhancements

### 1. API Gateway Integration
- Single `/api/v1/ingest` endpoint for all HKUST systems
- Dynamic JSON parsing for Event system, Attendance, 3rd-party forms
- Source system auto-detection
- Flexible field mapping

### 2. Waterfall Matching with Credibility
- TIER-1: Deterministic (100%)
- TIER-2: Probabilistic with source credibility multipliers
- TIER-3: Manual review for uncertain matches
- Confidence scoring combines match quality + source trustworthiness

### 3. Source Credibility Scoring
- CRM: 1.0 (100% trusted, master data)
- ADMS/Attendance: 0.9 (90% trusted)
- 3rd-party forms: 0.7 (70% trusted)
- Applied as multipliers to match confidence

### 4. Identity Stitching
- Relationship tracking (parent-child, duplicates, variants)
- Identity graph for complex scenarios
- Multi-source linking with credibility scores

### 5. Manual Review System
- Review queue for uncertain matches (50-95% confidence)
- Admin API for approving, rejecting, or merging
- Conflict resolution workflow
- Full audit trail

### 6. Monitoring & Observability
- Custom metrics for match success rate
- Latency tracking for <10s SLA
- Health checks
- Performance monitoring

---

## Success Criteria (Phase 1)

✅ Unified API Gateway handles Event system, Attendance, 3rd-party forms
✅ Waterfall matching engine with 3 tiers
✅ Source credibility scoring improves accuracy
✅ Manual review workflow handles uncertain matches
✅ <10 second latency on identity resolution
✅ 99% accuracy with false positive protection
✅ PDPO compliance with full audit trail
✅ CRM Inbox equivalent (review queue)
✅ Identity graph for complex relationships
✅ Production-ready with monitoring

---

## Timeline Estimate

**Phase 1 (Revised): 8-10 weeks**
- Weeks 1-2: API Gateway + Payload parsing
- Weeks 3-4: Waterfall engine + Credibility scoring
- Weeks 5-6: Manual review system + Identity stitching
- Weeks 7-8: Testing + Integration + Monitoring
- Weeks 9-10: Documentation + Deployment preparation

---

## Next Steps

1. **Approve this replan** ✅
2. **Start implementation** with API Gateway layer
3. **Build waterfall matching engine** with source credibility
4. **Implement manual review system**
5. **Add monitoring & observability**
6. **Comprehensive testing** (unit, integration, e2e)
7. **Deployment preparation**

---

## Questions to Confirm

1. Does this align with your blueprint?
2. Should we keep the same database (PostgreSQL) or consider alternatives?
3. Do you want Redis for caching in Phase 1 or Phase 2?
4. Should IAM/Midpoint integration be stubbed or fully integrated?
5. Are there specific HKUST data formats for Event system, Attendance?

