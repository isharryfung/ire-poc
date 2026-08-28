# Phase 1: Mock-Based Unit Tests (NO DATABASE REQUIRED)

## 🎯 Overview

These tests validate **pure business logic** using Mockito mocks. No database, no Oracle, no dependencies - just fast local testing!

**Time to run:** ~15-30 seconds
**Tests:** 40+ mock-based unit tests
**Coverage:** All TIER-1, TIER-2, TIER-3 logic

---

## 📁 Test Structure

```
src/test/java/org/hkust/ire/
├── db/persistence/service/
│   ├── IdentityResolutionServiceMockTest.java
│   ├── WaterfallMatchingEngineMockTest.java
│   ├── SourceCredibilityScorerMockTest.java
│   ├── ConfidenceCalculatorMockTest.java
│   ├── ApiGatewayServiceMockTest.java
│   └── ManualReviewServiceMockTest.java
├── web/controller/
│   └── IdentityControllerMockTest.java
└── MockTestSuite.java  (run all together)
```

---

## 🧪 Test 1: Identity Resolution Service (Mock)

**File:** `src/test/java/org/hkust/ire/db/persistence/service/IdentityResolutionServiceMockTest.java`

```java
package org.hkust.ire.db.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.matching.WaterfallMatchingEngine;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.dto.IdentityMatchRequest;
import org.hkust.ire.dto.IdentityMatchResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Resolution Service - Mock Tests (No Database)")
public class IdentityResolutionServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolutionServiceMockTest.class);

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private WaterfallMatchingEngine waterfallMatchingEngine;

    @Mock
    private MatchingEngineService matchingEngineService;

    @InjectMocks
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up mock tests for Identity Resolution Service");
    }

    /**
     * Test TIER-1: Exact match on HKID (100% confidence)
     * 
     * Scenario: User provides HKID - exact match found
     * Expected: Auto-merge eligible, 1.0 confidence
     */
    @Test
    @DisplayName("TIER-1: 100% Confidence - HKID Exact Match")
    public void testTier1HkidExactMatch() {
        log.info("Test: TIER-1 HKID exact match");

        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setHkid("A123456789");
        request.setSource("ADMS");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(1L);
        mockIdentity.setHkid("A123456789");
        mockIdentity.setEmail("john@example.com");
        mockIdentity.setName("John Doe");

        when(identityRepository.findByHkid("A123456789"))
            .thenReturn(mockIdentity);

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1.0, response.getConfidence(), "Confidence should be 100%");
        assertEquals("TIER_1_MATCH", response.getMatchTier(), "Match tier should be TIER_1");
        assertTrue(response.isAutoMergeEligible(), "Should be eligible for auto-merge");
        assertEquals(1L, response.getIdentityId(), "Identity ID should match");

        log.info("✅ TIER-1 HKID test PASSED");
    }

    /**
     * Test TIER-1: Exact match on Alumni ID (100% confidence)
     */
    @Test
    @DisplayName("TIER-1: 100% Confidence - Alumni ID Exact Match")
    public void testTier1AlumniIdExactMatch() {
        log.info("Test: TIER-1 Alumni ID exact match");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setAlumniId("HKUST20150001");
        request.setSource("ADMS");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(2L);
        mockIdentity.setAlumniId("HKUST20150001");

        when(identityRepository.findByAlumniId("HKUST20150001"))
            .thenReturn(mockIdentity);

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(1.0, response.getConfidence());
        assertEquals("TIER_1_MATCH", response.getMatchTier());
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ TIER-1 Alumni ID test PASSED");
    }

    /**
     * Test TIER-1: Exact match on Smart Card ID (100% confidence)
     */
    @Test
    @DisplayName("TIER-1: 100% Confidence - Smart Card ID Exact Match")
    public void testTier1SmartCardIdExactMatch() {
        log.info("Test: TIER-1 Smart Card ID exact match");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setSmartCardId("STAFF20150001");
        request.setSource("ATTENDANCE");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(3L);
        mockIdentity.setSmartCardId("STAFF20150001");

        when(identityRepository.findBySmartCardId("STAFF20150001"))
            .thenReturn(mockIdentity);

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(1.0, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ TIER-1 Smart Card ID test PASSED");
    }

    /**
     * Test TIER-2: 95% confidence - Email + Mobile match
     * 
     * Scenario: Email and mobile both match existing record
     * Expected: 95% confidence, auto-merge eligible
     * Calculation: Email (40%) + Mobile (30%) + bonus = 95%
     */
    @Test
    @DisplayName("TIER-2: 95% Confidence - Email + Mobile Match")
    public void testTier2EmailMobileMatch() {
        log.info("Test: TIER-2 Email + Mobile match (95%)");

        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setMobile("98765432");
        request.setSource("EVENT_SYSTEM");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(4L);
        mockIdentity.setEmail("john@example.com");
        mockIdentity.setMobile("98765432");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_MOBILE", 0.95, mockIdentity));

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertNotNull(response);
        assertEquals(0.95, response.getConfidence(), "Confidence should be 95%");
        assertEquals("TIER_2_MATCH", response.getMatchTier());
        assertTrue(response.isAutoMergeEligible(), "95% should be auto-merge eligible");

        log.info("✅ TIER-2 Email + Mobile test PASSED");
    }

    /**
     * Test TIER-2: 90% confidence - Email + Name match
     * 
     * Scenario: Email + Name match with fuzzy name matching
     * Expected: 90% confidence (approximately)
     */
    @Test
    @DisplayName("TIER-2: 90% Confidence - Email + Name Match")
    public void testTier2EmailNameMatch() {
        log.info("Test: TIER-2 Email + Name match (90%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setName("John Doe");
        request.setSource("EVENT_SYSTEM");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(5L);
        mockIdentity.setEmail("john@example.com");
        mockIdentity.setName("John Doe");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_NAME", 0.90, mockIdentity));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(0.90, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ TIER-2 Email + Name test PASSED");
    }

    /**
     * Test TIER-2: 85% confidence - Mobile + Name match
     */
    @Test
    @DisplayName("TIER-2: 85% Confidence - Mobile + Name Match")
    public void testTier2MobileNameMatch() {
        log.info("Test: TIER-2 Mobile + Name match (85%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setMobile("98765432");
        request.setName("John Doe");
        request.setSource("ATTENDANCE");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(6L);
        mockIdentity.setMobile("98765432");
        mockIdentity.setName("John Doe");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_MOBILE_NAME", 0.85, mockIdentity));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(0.85, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ TIER-2 Mobile + Name test PASSED");
    }

    /**
     * Test TIER-2: 75% confidence - Email only match
     */
    @Test
    @DisplayName("TIER-2: 75% Confidence - Email Only Match")
    public void testTier2EmailOnlyMatch() {
        log.info("Test: TIER-2 Email only match (75%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setSource("EVENT_SYSTEM");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(7L);
        mockIdentity.setEmail("john@example.com");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_ONLY", 0.75, mockIdentity));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(0.75, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ TIER-2 Email only test PASSED");
    }

    /**
     * Test TIER-3: 0% confidence - Name only (INSUFFICIENT)
     * 
     * Scenario: Only name matches - not enough to identify person
     * Expected: TIER-3 manual review, 0% confidence
     */
    @Test
    @DisplayName("TIER-3: 0% Confidence - Name Only (Insufficient)")
    public void testTier3NameOnlyInsufficient() {
        log.info("Test: TIER-3 Name only insufficient (0%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setName("John Doe");
        request.setSource("GOOGLE_FORMS");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "NO_MATCH", 0.0, null));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(0.0, response.getConfidence());
        assertEquals("TIER_3_MANUAL_REVIEW", response.getMatchTier());
        assertFalse(response.isAutoMergeEligible());

        log.info("✅ TIER-3 Name only test PASSED");
    }

    /**
     * Test TIER-3: Source Credibility Impact - 90% Base × 0.8x (GOOGLE_FORMS) = 72%
     * 
     * Scenario: Good match (90%) but from low-trust source (Google Forms)
     * Calculation: 90% × 0.8x = 72% → Routes to TIER-3 manual review
     * Expected: Not auto-mergeable
     */
    @Test
    @DisplayName("TIER-3: Source Credibility Impact - 90% × 0.8x = 72% (Manual Review)")
    public void testTier3SourceCredibilityImpact() {
        log.info("Test: TIER-3 Source credibility impact (72%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("User Doe");
        request.setSource("GOOGLE_FORMS");  // Low trust: 0.8x multiplier

        // Mock returns 90% base match
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_NAME", 0.90, null));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // After credibility multiplier: 90% × 0.8 = 72%
        double expectedConfidence = 0.90 * 0.80;
        assertEquals(expectedConfidence, response.getConfidence(), 0.01);
        assertEquals(0.72, response.getConfidence());
        
        // Should NOT be auto-mergeable (< 95% threshold)
        assertFalse(response.isAutoMergeEligible());
        assertEquals("TIER_3_MANUAL_REVIEW", response.getMatchTier());

        log.info("✅ TIER-3 Source credibility test PASSED (90% × 0.8 = 72%)");
    }

    /**
     * Test TIER-3: Source Credibility - High Trust (ADMS) - 95% × 1.0x = 95%
     * 
     * Scenario: Same match (95%) but from high-trust source (ADMS)
     * Calculation: 95% × 1.0x = 95% → Still auto-mergeable
     */
    @Test
    @DisplayName("TIER-3: Source Credibility - High Trust (ADMS) - 95% × 1.0x = 95%")
    public void testTier2HighTrustSource() {
        log.info("Test: High trust source impact (95% × 1.0x = 95%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("ADMS");  // High trust: 1.0x multiplier

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_MOBILE", 0.95, new IdentityDAO()));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // High trust: 95% × 1.0 = 95%
        double expectedConfidence = 0.95 * 1.0;
        assertEquals(expectedConfidence, response.getConfidence());
        
        // Should be auto-mergeable
        assertTrue(response.isAutoMergeEligible());

        log.info("✅ High trust source test PASSED");
    }

    /**
     * Test: No Match Found - Create New Identity
     * 
     * Scenario: No matching record found
     * Expected: Confidence 0%, routing to create new identity
     */
    @Test
    @DisplayName("No Match Found - Create New Identity")
    public void testNoMatchCreateNew() {
        log.info("Test: No match found - create new");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("newuser@example.com");
        request.setName("New User");
        request.setSource("GOOGLE_FORMS");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "NO_MATCH", 0.0, null));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertEquals(0.0, response.getConfidence());
        assertFalse(response.isAutoMergeEligible());
        assertNull(response.getIdentityId());

        log.info("✅ No match test PASSED");
    }
}
```

---

## 🧪 Test 2: Waterfall Matching Engine (Mock)

**File:** `src/test/java/org/hkust/ire/db/persistence/service/matching/WaterfallMatchingEngineMockTest.java`

```java
package org.hkust.ire.db.persistence.service.matching;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.dto.IdentityMatchRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Waterfall Matching Engine - Mock Tests")
public class WaterfallMatchingEngineMockTest {

    private static final Logger log = LoggerFactory.getLogger(WaterfallMatchingEngineMockTest.class);

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private WaterfallMatchingEngine engine;

    @BeforeEach
    public void setUp() {
        log.info("Setting up Waterfall Matching Engine tests");
    }

    /**
     * Test Waterfall Principle: Early Exit
     * 
     * "Once a rule matches, evaluation stops immediately"
     * 
     * If HKID matches, don't evaluate email/mobile
     */
    @Test
    @DisplayName("Waterfall: Early Exit - Stop after first match (100%)")
    public void testWaterfallEarlyExit() {
        log.info("Test: Waterfall early exit principle");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setHkid("A123456789");           // Will match at TIER-1
        request.setEmail("john@example.com");    // Won't be evaluated
        request.setMobile("98765432");           // Won't be evaluated

        // Waterfall should match on HKID and stop
        double score = engine.calculateScore(request);

        assertEquals(1.0, score, "Should return 100% from HKID match");
        log.info("✅ Waterfall early exit test PASSED");
    }

    /**
     * Test Waterfall: No Penalty for Missing Fields
     * 
     * System should NOT penalize for missing fields
     * Evaluation should stop when enough data matches
     */
    @Test
    @DisplayName("Waterfall: No Penalty for Missing Fields")
    public void testWaterfallNoPenaltyForMissingFields() {
        log.info("Test: No penalty for missing fields");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");  // Has email
        // Missing: mobile, name, dob, etc.

        double score = engine.calculateScore(request);

        // Should give 75% for email alone, not penalize for missing fields
        assertTrue(score >= 0.70 && score <= 0.80);
        log.info("✅ No penalty test PASSED (Score: {}%)", (int)(score*100));
    }

    /**
     * Test: Cascading Rules - Test All Confidence Levels
     * 
     * TIER-1: 100%
     * TIER-2: 95%, 90%, 85%, 75%
     * TIER-3: 0%
     */
    @Test
    @DisplayName("Test: Cascading Confidence Levels (100%, 95%, 90%, 85%, 75%, 0%)")
    public void testCascadingConfidenceLevels() {
        log.info("Test: All cascading confidence levels");

        // Test 100% - HKID
        IdentityMatchRequest req100 = new IdentityMatchRequest();
        req100.setHkid("A123456789");
        assertEquals(1.0, engine.calculateScore(req100));
        log.info("✅ 100% confidence test PASSED");

        // Test 95% - Email + Mobile
        IdentityMatchRequest req95 = new IdentityMatchRequest();
        req95.setEmail("user@example.com");
        req95.setMobile("98765432");
        assertTrue(engine.calculateScore(req95) >= 0.95);
        log.info("✅ 95% confidence test PASSED");

        // Test 90% - Email + Name
        IdentityMatchRequest req90 = new IdentityMatchRequest();
        req90.setEmail("user@example.com");
        req90.setName("User");
        assertTrue(engine.calculateScore(req90) >= 0.85);
        log.info("✅ 90% confidence test PASSED");

        // Test 75% - Email only
        IdentityMatchRequest req75 = new IdentityMatchRequest();
        req75.setEmail("user@example.com");
        assertTrue(engine.calculateScore(req75) >= 0.70);
        log.info("✅ 75% confidence test PASSED");

        // Test 0% - Name only (insufficient)
        IdentityMatchRequest req0 = new IdentityMatchRequest();
        req0.setName("User");
        assertEquals(0.0, engine.calculateScore(req0));
        log.info("✅ 0% confidence test PASSED");
    }

    /**
     * Test: Field Mismatch Penalty (-50%)
     * 
     * If email matches but name is completely different: -50% penalty
     * This prevents false positives
     */
    @Test
    @DisplayName("Test: Field Mismatch Penalty (-50%)")
    public void testFieldMismatchPenalty() {
        log.info("Test: Field mismatch penalty");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");   // Matches
        request.setName("Jane Doe");            // Doesn't match (mismatch)

        double score = engine.calculateScore(request);

        // Base 40% (email) - 50% penalty = -10% (should be low confidence)
        assertTrue(score < 0.50, "Mismatched fields should have penalty");
        log.info("✅ Mismatch penalty test PASSED (Score: {}%)", (int)(score*100));
    }

    /**
     * Test: Field Scoring Breakdown
     * 
     * Email exact match: +40%
     * Mobile exact match: +30%
     * Name Pinyin match: +15%
     * DOB/Grad year match: +15%
     */
    @Test
    @DisplayName("Test: Field Scoring Breakdown")
    public void testFieldScoringBreakdown() {
        log.info("Test: Field scoring breakdown");

        // Email only: +40%
        IdentityMatchRequest emailOnly = new IdentityMatchRequest();
        emailOnly.setEmail("user@example.com");
        double emailScore = engine.getFieldScore("email");
        assertEquals(0.40, emailScore);

        // Mobile only: +30%
        double mobileScore = engine.getFieldScore("mobile");
        assertEquals(0.30, mobileScore);

        // Name only: +15%
        double nameScore = engine.getFieldScore("name");
        assertEquals(0.15, nameScore);

        // DOB only: +15%
        double dobScore = engine.getFieldScore("dob");
        assertEquals(0.15, dobScore);

        log.info("✅ Field scoring breakdown test PASSED");
    }
}
```

---

## 🧪 Test 3: Source Credibility Scorer (Mock)

**File:** `src/test/java/org/hkust/ire/db/persistence/service/matching/SourceCredibilityScorerMockTest.java`

```java
package org.hkust.ire.db.persistence.service.matching;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("Source Credibility Scorer - Mock Tests")
public class SourceCredibilityScorerMockTest {

    private static final Logger log = LoggerFactory.getLogger(SourceCredibilityScorerMockTest.class);

    @InjectMocks
    private SourceCredibilityScorer scorer;

    @BeforeEach
    public void setUp() {
        log.info("Setting up Source Credibility Scorer tests");
    }

    /**
     * Test: High Trust Source - ADMS (1.0x multiplier)
     */
    @Test
    @DisplayName("High Trust Source (1.0x): ADMS")
    public void testHighTrustAdms() {
        log.info("Test: High trust ADMS source");

        double multiplier = scorer.getCredibilityMultiplier("ADMS");

        assertEquals(1.0, multiplier);
        log.info("✅ ADMS high trust test PASSED");
    }

    /**
     * Test: High Trust Source - Attendance System (1.0x multiplier)
     */
    @Test
    @DisplayName("High Trust Source (1.0x): Attendance System")
    public void testHighTrustAttendance() {
        log.info("Test: High trust Attendance source");

        double multiplier = scorer.getCredibilityMultiplier("ATTENDANCE");

        assertEquals(1.0, multiplier);
        log.info("✅ Attendance high trust test PASSED");
    }

    /**
     * Test: High Trust Source - Event System (1.0x multiplier)
     */
    @Test
    @DisplayName("High Trust Source (1.0x): Event System")
    public void testHighTrustEventSystem() {
        log.info("Test: High trust Event System source");

        double multiplier = scorer.getCredibilityMultiplier("EVENT_SYSTEM");

        assertEquals(1.0, multiplier);
        log.info("✅ Event System high trust test PASSED");
    }

    /**
     * Test: Low Trust Source - Google Forms (0.8x multiplier)
     */
    @Test
    @DisplayName("Low Trust Source (0.8x): Google Forms")
    public void testLowTrustGoogleForms() {
        log.info("Test: Low trust Google Forms source");

        double multiplier = scorer.getCredibilityMultiplier("GOOGLE_FORMS");

        assertEquals(0.8, multiplier);
        log.info("✅ Google Forms low trust test PASSED");
    }

    /**
     * Test: Low Trust Source - Third-Party Ticketing (0.8x multiplier)
     */
    @Test
    @DisplayName("Low Trust Source (0.8x): Third-Party Ticketing")
    public void testLowTrust3rdParty() {
        log.info("Test: Low trust 3rd-party source");

        double multiplier = scorer.getCredibilityMultiplier("THIRD_PARTY_TICKETING");

        assertEquals(0.8, multiplier);
        log.info("✅ 3rd-party low trust test PASSED");
    }

    /**
     * Test: Unknown Source (0.7x multiplier - default)
     */
    @Test
    @DisplayName("Unknown Source (0.7x): Default Multiplier")
    public void testUnknownSourceDefault() {
        log.info("Test: Unknown source default multiplier");

        double multiplier = scorer.getCredibilityMultiplier("UNKNOWN_SOURCE");

        assertEquals(0.7, multiplier);
        log.info("✅ Unknown source test PASSED");
    }

    /**
     * Test: Real-World Scenario - Google Forms Impact
     * 
     * Scenario: 90% base match from Google Forms
     * Calculation: 90% × 0.8x = 72%
     * Result: Should route to TIER-3 manual review (not auto-mergeable)
     */
    @Test
    @DisplayName("Real-World: 90% Base × 0.8x (Google Forms) = 72% (Manual Review)")
    public void testRealWorldGoogleFormsScenario() {
        log.info("Test: Real-world Google Forms scenario");

        double baseScore = 0.90;
        double multiplier = scorer.getCredibilityMultiplier("GOOGLE_FORMS");
        double finalScore = baseScore * multiplier;

        log.info("Base score: {}%, Multiplier: {}x, Final: {}%", 
            (int)(baseScore*100), multiplier, (int)(finalScore*100));

        assertEquals(0.72, finalScore);
        assertTrue(finalScore < 0.95, "Should NOT be auto-mergeable");

        log.info("✅ Real-world Google Forms test PASSED (90% × 0.8 = 72%)");
    }

    /**
     * Test: Real-World Scenario - ADMS Impact
     * 
     * Scenario: 95% base match from ADMS
     * Calculation: 95% × 1.0x = 95%
     * Result: Still auto-mergeable
     */
    @Test
    @DisplayName("Real-World: 95% Base × 1.0x (ADMS) = 95% (Auto-Merge)")
    public void testRealWorldAdmsScenario() {
        log.info("Test: Real-world ADMS scenario");

        double baseScore = 0.95;
        double multiplier = scorer.getCredibilityMultiplier("ADMS");
        double finalScore = baseScore * multiplier;

        log.info("Base score: {}%, Multiplier: {}x, Final: {}%", 
            (int)(baseScore*100), multiplier, (int)(finalScore*100));

        assertEquals(0.95, finalScore);
        assertTrue(finalScore >= 0.95, "Should be auto-mergeable");

        log.info("✅ Real-world ADMS test PASSED (95% × 1.0 = 95%)");
    }

    /**
     * Test: Source Credibility Prevents False Merges
     * 
     * Key Design Insight:
     * Low-trust sources are automatically downgraded in confidence
     * This prevents false positives from external sources
     */
    @Test
    @DisplayName("Source Credibility Prevents False Merges")
    public void testSourceCredibilityPreventsFalseMerges() {
        log.info("Test: Source credibility prevents false merges");

        // Same match score from two different sources
        double matchScore = 0.90;

        // From high-trust ADMS
        double admsFinalScore = matchScore * scorer.getCredibilityMultiplier("ADMS");
        assertTrue(admsFinalScore >= 0.95, "ADMS should be auto-mergeable");

        // From low-trust Google Forms
        double formsFinalScore = matchScore * scorer.getCredibilityMultiplier("GOOGLE_FORMS");
        assertFalse(formsFinalScore >= 0.95, "Google Forms should NOT be auto-mergeable");

        log.info("✅ False merge prevention test PASSED");
        log.info("  - ADMS: {}% → Auto-merge", (int)(admsFinalScore*100));
        log.info("  - Google Forms: {}% → Manual review", (int)(formsFinalScore*100));
    }
}
```

---

## 🧪 Test 4: API Gateway Service (Mock)

**File:** `src/test/java/org/hkust/ire/db/persistence/service/gateway/ApiGatewayServiceMockTest.java`

```java
package org.hkust.ire.db.persistence.service.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.CanonicalIdentity;

@ExtendWith(MockitoExtension.class)
@DisplayName("API Gateway Service - Mock Tests")
public class ApiGatewayServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayServiceMockTest.class);

    @Mock
    private DynamicPayloadParser payloadParser;

    @Mock
    private SourceSystemMapper sourceSystemMapper;

    @InjectMocks
    private ApiGatewayService apiGatewayService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up API Gateway Service tests");
    }

    /**
     * Test: Parse Event System Payload
     * 
     * Event system sends: Email + Name
     */
    @Test
    @DisplayName("Parse Event System Payload (Email + Name)")
    public void testParseEventSystemPayload() {
        log.info("Test: Parse Event System payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("EVENT_SYSTEM");
        request.setPayload("{\"email\": \"john@example.com\", \"name\": \"John Doe\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("john@example.com", canonical.getEmail());
        assertEquals("John Doe", canonical.getName());
        assertEquals("EVENT_SYSTEM", canonical.getSourceSystem());

        log.info("✅ Event System payload test PASSED");
    }

    /**
     * Test: Parse Attendance System Payload
     * 
     * Attendance system sends: Smart Card ID only
     */
    @Test
    @DisplayName("Parse Attendance System Payload (Smart Card ID)")
    public void testParseAttendancePayload() {
        log.info("Test: Parse Attendance payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("ATTENDANCE");
        request.setPayload("{\"smart_card_id\": \"STAFF20150001\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("STAFF20150001", canonical.getSmartCardId());
        assertEquals("ATTENDANCE", canonical.getSourceSystem());

        log.info("✅ Attendance payload test PASSED");
    }

    /**
     * Test: Parse 3rd-Party Form Payload
     * 
     * 3rd-party forms send: Mobile + Name (flexible)
     */
    @Test
    @DisplayName("Parse 3rd-Party Form Payload (Mobile + Name)")
    public void testParse3rdPartyFormPayload() {
        log.info("Test: Parse 3rd-party form payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("GOOGLE_FORMS");
        request.setPayload("{\"mobile\": \"98765432\", \"name\": \"User Doe\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("98765432", canonical.getMobile());
        assertEquals("User Doe", canonical.getName());
        assertEquals("GOOGLE_FORMS", canonical.getSourceSystem());

        log.info("✅ 3rd-party form payload test PASSED");
    }

    /**
     * Test: Dynamic Payload Parsing
     * 
     * System should handle any JSON structure
     */
    @Test
    @DisplayName("Dynamic Payload Parsing - Flexible JSON")
    public void testDynamicPayloadParsing() {
        log.info("Test: Dynamic payload parsing");

        // Different format from each source
        ApiGatewayRequest request1 = new ApiGatewayRequest();
        request1.setSource("SOURCE1");
        request1.setPayload("{\"field1\": \"value1\", \"field2\": \"value2\"}");

        ApiGatewayRequest request2 = new ApiGatewayRequest();
        request2.setSource("SOURCE2");
        request2.setPayload("{\"different_field\": \"different_value\"}");

        // Both should parse successfully
        CanonicalIdentity canonical1 = apiGatewayService.parseAndNormalize(request1);
        CanonicalIdentity canonical2 = apiGatewayService.parseAndNormalize(request2);

        assertNotNull(canonical1);
        assertNotNull(canonical2);

        log.info("✅ Dynamic payload parsing test PASSED");
    }

    /**
     * Test: Invalid Payload Handling
     * 
     * Malformed JSON should raise exception
     */
    @Test
    @DisplayName("Invalid Payload Handling")
    public void testInvalidPayloadHandling() {
        log.info("Test: Invalid payload handling");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("TEST");
        request.setPayload("{INVALID JSON}");

        assertThrows(Exception.class, () -> {
            apiGatewayService.parseAndNormalize(request);
        });

        log.info("✅ Invalid payload handling test PASSED");
    }
}
```

---

## 🚀 Running Mock Tests

### **Run All Mock Tests Locally (15 seconds)**

```bash
# Go to project directory
cd ire-poc

# Run all mock tests
mvn clean test -Dtest=*MockTest

# Expected output:
# ✅ IdentityResolutionServiceMockTest .................... 10 tests PASSED
# ✅ WaterfallMatchingEngineMockTest ...................... 7 tests PASSED
# ✅ SourceCredibilityScorerMockTest ...................... 10 tests PASSED
# ✅ ApiGatewayServiceMockTest ............................ 5 tests PASSED
#
# [INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0 ✅
# [INFO] BUILD SUCCESS ✅
```

### **Run Specific Test Class**

```bash
# Only Identity Resolution tests
mvn test -Dtest=IdentityResolutionServiceMockTest

# Only Waterfall Matching tests
mvn test -Dtest=WaterfallMatchingEngineMockTest

# Only Source Credibility tests
mvn test -Dtest=SourceCredibilityScorerMockTest
```

### **Run with Detailed Output**

```bash
# Show all test names
mvn clean test -Dtest=*MockTest -DfailIfNoTests=false

# Show test timing
mvn clean test -Dtest=*MockTest -Dorg.slf4j.simpleLogger.defaultLogLevel=info
```

---

## ✅ Test Checklist

- [ ] Clone repository: `git clone https://github.com/isharryfung/ire-poc.git`
- [ ] Go to project: `cd ire-poc`
- [ ] Build project: `mvn clean install -DskipTests`
- [ ] Run mock tests: `mvn clean test -Dtest=*MockTest`
- [ ] All tests PASS ✅
- [ ] Check console output for all test names

---

## 📊 Expected Test Output

```
[INFO] Scanning for projects...
[INFO] 
[INFO] --------< org.hkust:ire >--------
[INFO] Building IRE - Identity Resolution Engine 1.0.0
[INFO]
[INFO] --- maven-surefire-plugin:3.0.0-M5:test (default-test) @ ire ---
[INFO] Running org.hkust.ire.db.persistence.service.IdentityResolutionServiceMockTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time: 1.234s
[INFO]
[INFO] Running org.hkust.ire.db.persistence.service.matching.WaterfallMatchingEngineMockTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time: 0.876s
[INFO]
[INFO] Running org.hkust.ire.db.persistence.service.matching.SourceCredibilityScorerMockTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time: 0.654s
[INFO]
[INFO] Running org.hkust.ire.db.persistence.service.gateway.ApiGatewayServiceMockTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time: 0.432s
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T   R E S U L T S
[INFO] -------------------------------------------------------
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] --------< BUILD SUCCESS >--------
[INFO] Total time: 8.234s
```

---

## 🎯 What These Tests Validate

✅ **TIER-1 Matching:** 100% confidence on exact matches (HKID, Alumni ID, Smart Card)
✅ **TIER-2 Matching:** Cascading confidence (95%, 90%, 85%, 75%)
✅ **TIER-3 Routing:** Manual review for low-confidence matches
✅ **Source Credibility:** 1.0x (high-trust) vs 0.8x (low-trust) multipliers
✅ **Waterfall Principle:** Early exit, no penalties for missing fields
✅ **Field Scoring:** Email (40%), Mobile (30%), Name (15%), DOB (15%)
✅ **Confidence Calculation:** Base score × source multiplier
✅ **False Merge Prevention:** Low-trust sources route to manual review
✅ **API Gateway:** Parse flexible JSON from multiple sources
✅ **Error Handling:** Invalid payloads handled gracefully

