# 🧪 `/ingest` Endpoint - Local Testing Guide

**Endpoint:** `POST /api/v1/ingest`  
**Base URL:** `http://localhost:8080`  
**Authentication:** Default disabled in dev profile (see `application.properties`)

---

## 🚀 Quick Start - 3 Steps

### Step 1: Start Application
```bash
cd your-ire-poc-repo
mvn clean install
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

Wait for: `Started IreApplication in X seconds`

### Step 2: Verify Application is Running
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### Step 3: Run Test Request (See below for examples)

---

## 📋 Test Requests & Data

### Test 1: CRM TIER-1 Match (Exact Email Match) ✅

**Scenario:** New CRM record with email matching existing identity → Auto-merge (100% confidence)

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-20150001",
    "requestId": "REQ-CRM-001",
    "payload": {
      "email": "john.doe@hkust.edu.hk",
      "firstName": "John",
      "lastName": "Doe",
      "hkid": "A123456789"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": "GID-XXXXXXXX",
  "matchTier": "TIER_1",
  "confidenceScore": 1.0,
  "status": "RESOLVED"
}
```

**HTTP Status:** `200 OK`  
**Action:** Creates new golden record (first time) → Links source to golden

---

### Test 2: Attendance System TIER-1 (Staff ID) ✅

**Scenario:** Attendance system sends staff ID that matches existing identity

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "ATTENDANCE",
    "sourceId": "STAFF20150001",
    "requestId": "REQ-ATT-001",
    "payload": {
      "staffId": "STAFF20150001",
      "firstName": "Jane",
      "lastName": "Smith"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": "GID-STAFF001",
  "matchTier": "TIER_1",
  "confidenceScore": 1.0,
  "status": "RESOLVED"
}
```

**HTTP Status:** `200 OK`

---

### Test 3: Event System TIER-1 (Email Match) ✅

**Scenario:** Event system sends email + name → Matches existing identity

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "EVENT_SYSTEM",
    "sourceId": "EVT-2026-001",
    "requestId": "REQ-EVT-001",
    "payload": {
      "email": "event.user@hkust.edu.hk",
      "firstName": "Event",
      "lastName": "User",
      "phone": "98765432"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": "GID-EVENT001",
  "matchTier": "TIER_1",
  "confidenceScore": 1.0,
  "status": "RESOLVED"
}
```

**HTTP Status:** `200 OK`

---

### Test 4: Third-Party Form (Google Forms) - Low Trust ⚠️

**Scenario:** Google Forms submission with name only (insufficient data) → TIER-3 Manual Review

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "GOOGLE_FORMS",
    "sourceId": "GFORM-20260513-001",
    "requestId": "REQ-GFORM-001",
    "payload": {
      "firstName": "Anonymous",
      "lastName": "User",
      "phone": "92000000"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": null,
  "matchTier": "TIER_3",
  "confidenceScore": 0.0,
  "status": "REVIEW_REQUIRED",
  "message": "Identity routed to manual review"
}
```

**HTTP Status:** `200 OK` (still successful, but routed to review)

---

### Test 5: Duplicate Ingest (Same Email) - TIER-1 Link ✅

**Scenario:** Second source system ingests same person (same email) → Links to existing golden ID

**First request (creates golden):**
```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-DUP-001",
    "requestId": "REQ-DUP-001",
    "payload": {
      "email": "duplicate@hkust.edu.hk",
      "firstName": "Duplicate",
      "lastName": "Person"
    }
  }'
```

Response: `{"success": true, "goldenId": "GID-DUP001", "matchTier": "TIER_1", ...}`

**Second request (different system, same person):**
```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "ADMS",
    "sourceId": "ADMS-DUP-001",
    "requestId": "REQ-DUP-002",
    "payload": {
      "email": "duplicate@hkust.edu.hk",
      "studentId": "20123456"
    }
  }'
```

Response: `{"success": true, "goldenId": "GID-DUP001", "matchTier": "TIER_1", ...}` ← **Same goldenId!**

---

### Test 6: New Identity Creation (No Existing Record) 🆕

**Scenario:** Email not in database → Creates new golden record

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-NEW-001",
    "requestId": "REQ-NEW-001",
    "payload": {
      "email": "brandnew@hkust.edu.hk",
      "firstName": "Brand",
      "lastName": "New",
      "hkid": "B987654321"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": "GID-XXXXXXXX",
  "matchTier": "TIER_1",
  "confidenceScore": 1.0,
  "status": "RESOLVED"
}
```

**HTTP Status:** `200 OK`  
**Database:** New record created in `identities` table

---

### Test 7: Minimal Payload (Email Only) ✅

**Scenario:** Minimal valid payload with just email

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-MINIMAL-001",
    "requestId": "REQ-MIN-001",
    "payload": {
      "email": "minimal@hkust.edu.hk"
    }
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "goldenId": "GID-MIN001",
  "matchTier": "TIER_1",
  "confidenceScore": 1.0,
  "status": "RESOLVED"
}
```

**HTTP Status:** `200 OK`

---

### Test 8: Empty Payload (Should Fail) ❌

**Scenario:** Empty payload → Validation error

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-EMPTY-001",
    "payload": {}
  }'
```

**Expected Response:**
```json
{
  "success": false,
  "message": "payload must not be empty"
}
```

**HTTP Status:** `400 Bad Request`

---

### Test 9: Invalid JSON (Should Fail) ❌

**Scenario:** Malformed JSON → Parse error

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "CRM-BAD-001",
    "payload": { invalid json'
```

**Expected Response:**
```json
{
  "success": false,
  "message": "JSON parse error..."
}
```

**HTTP Status:** `400 Bad Request`

---

### Test 10: Missing Source System (Should Fail) ❌

**Scenario:** No source system specified → Validation error

```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "MISSING-001",
    "payload": {
      "email": "test@hkust.edu.hk"
    }
  }'
```

**Expected Response:**
```json
{
  "success": false,
  "message": "sourceSystem is required"
}
```

**HTTP Status:** `400 Bad Request`

---

## 🛠️ Using Postman/Insomnia

### Setup:

1. **Create new POST request**
   - URL: `http://localhost:8080/api/v1/ingest`
   - Method: `POST`
   - Headers: `Content-Type: application/json`

2. **Use any test request body from above**

3. **Click Send**

---

## 📊 All Field Mappings (DynamicPayloadParser)

The parser recognizes these field names (case-insensitive):

| Canonical Field | Acceptable JSON Keys |
|-----------------|----------------------|
| `hkid` | `hkid`, `HKID` |
| `staffId` | `staffId`, `staff_id`, `STAFF_ID` |
| `studentId` | `studentId`, `student_id`, `STUDENT_ID` |
| `email` | `email`, `EMAIL`, `emailAddress` |
| `firstName` | `firstName`, `first_name`, `givenName` |
| `lastName` | `lastName`, `last_name`, `surname` |
| `phone` | `phone`, `telephone`, `mobile` |

**Example:** Any of these work:
```json
{"email": "test@hkust.edu.hk"}
{"EMAIL": "test@hkust.edu.hk"}
{"emailAddress": "test@hkust.edu.hk"}
```

---

## 🔍 Debugging & Troubleshooting

### Check Logs
```bash
tail -f logs/app.log
# or
tail -100f logs/app.log | grep -i "ingest\|resolve\|matching"
```

### Look for These Messages:
✅ **Success:**
```
Received ingest request from sourceSystem=CRM
Processing ingest request from sourceSystem=CRM, requestId=REQ-CRM-001
Identity resolved: goldenId=GID-XXXXXXXX, tier=TIER_1, score=1.0
```

❌ **Errors:**
```
Error processing ingest request: ...
Payload validation failed for sourceSystem=...
Error resolving identity: ...
```

### Check Database

```sql
-- H2 Web Console: http://localhost:8080/h2-console
-- JDBC URL: jdbc:h2:mem:iredb

-- Check identities
SELECT * FROM identities ORDER BY created_at DESC;

-- Check identity links
SELECT * FROM identity_links ORDER BY created_at DESC;

-- Check audit logs
SELECT * FROM audit_logs WHERE action = 'IDENTITY_RESOLVED' ORDER BY created_at DESC;
```

---

## 📈 Response Fields Explained

```json
{
  "success": true,              // ✅ Request succeeded
  "goldenId": "GID-ABC123",    // Golden identity ID (null if TIER-3)
  "matchTier": "TIER_1",        // TIER_1, TIER_2, or TIER_3
  "confidenceScore": 1.0,       // 0.0 to 1.0 (0% to 100%)
  "status": "RESOLVED",         // RESOLVED, REVIEW_REQUIRED, ERROR, MATCHED
  "message": "..."              // Optional error/info message
}
```

### Status Legend:
- **RESOLVED** ✅ - Identity successfully resolved
- **REVIEW_REQUIRED** ⚠️ - Routed to manual review (TIER-3)
- **MATCHED** ✅ - Match found
- **ERROR** ❌ - Error occurred
- **NEW_IDENTITY** 🆕 - New golden record created

---

## 🔄 Waterfall Matching Logic

```
┌─ Check TIER-1 (Exact Match)
│  ├─ HKID match? → 100% confidence
│  ├─ Staff ID match? → 100% confidence
│  └─ Email + Name exact match? → 100% confidence
│
├─ No TIER-1? Check TIER-2 (Probabilistic)
│  ├─ Email fuzzy match? → Calculate score
│  ├─ Phone match? → Calculate score
│  └─ Apply source credibility multiplier
│
└─ No good match? TIER-3 (Manual Review)
   └─ Route to review queue for admin
```

---

## 📋 Test Checklist

Run through these systematically:

- [ ] **Test 1 (CRM TIER-1)** - Creates first golden record
- [ ] **Test 2 (Attendance TIER-1)** - Staff ID match
- [ ] **Test 3 (Event System)** - Email match
- [ ] **Test 4 (Google Forms)** - TIER-3 routing
- [ ] **Test 5 (Duplicate)** - Same person, different source
- [ ] **Test 6 (New Identity)** - Creates new record
- [ ] **Test 7 (Minimal)** - Email only
- [ ] **Test 8 (Empty)** - Validation error
- [ ] **Test 9 (Bad JSON)** - Parse error
- [ ] **Test 10 (Missing Field)** - Validation error
- [ ] **Database check** - Verify records created
- [ ] **Logs check** - No "rollback-only" errors!

---

## ✅ Success Criteria

All tests pass when:
- ✅ Response HTTP status 200 OK
- ✅ Response has `success: true` (or false for error tests)
- ✅ Response has `goldenId` (except TIER-3)
- ✅ Response has `matchTier` and `confidenceScore`
- ✅ No "rollback-only" errors in logs
- ✅ Database records created correctly
- ✅ All 10 tests pass

---

## 🚀 Ready to Test!

Pick any test above and run it. Start with **Test 1 (CRM TIER-1)** to verify basic functionality.

**Questions?** Check the logs first: `tail -f logs/app.log | grep -i error`

---

**Last Updated:** 2026-05-13  
**Status:** ✅ Ready for Testing
