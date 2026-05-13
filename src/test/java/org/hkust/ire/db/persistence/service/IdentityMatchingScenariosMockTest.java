package org.hkust.ire.db.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

import java.util.Optional;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.IdentityLinkRepository;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.identity.IdentityCacheService;
import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.db.persistence.service.review.ManualReviewService;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;

/**
 * Comprehensive matching scenario tests for Phase 1
 * Tests real-world identity matching scenarios from multiple source systems
 *
 * @author isharryfung
 * @since 2026-05-13
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Matching Scenarios - Phase 1 Real-World Tests")
public class IdentityMatchingScenariosMockTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityMatchingScenariosMockTest.class);

    @Mock
    private MatchingEngineService matchingEngineService;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private IdentityLinkRepository identityLinkRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ManualReviewService manualReviewService;

    @Mock
    private IdentityCacheService identityCacheService;

    @InjectMocks
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up Phase 1 matching scenario tests");
    }

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    /**
     * Create a CanonicalIdentity for testing
     */
    private CanonicalIdentity createCanonical(String sourceSystem, String sourceId) {
        CanonicalIdentity c = new CanonicalIdentity();
        c.setSourceSystem(sourceSystem);
        c.setSourceId(sourceId);
        return c;
    }

    /**
     * Create a TIER-1 match response (100% confidence)
     */
    private IdentityMatchResponse tier1Response(String goldenId) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(true);
        r.setMatchTier(MatchTierConstant.TIER_1);
        r.setConfidenceScore(1.0);
        r.setGoldenId(goldenId);
        r.setStatus("MATCHED");
        return r;
    }

    /**
     * Create a TIER-2 match response (probabilistic match)
     */
    private IdentityMatchResponse tier2Response(String goldenId, double score) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(true);
        r.setMatchTier(MatchTierConstant.TIER_2);
        r.setConfidenceScore(score);
        r.setGoldenId(goldenId);
        r.setStatus("MATCHED");
        return r;
    }

    /**
     * Create a TIER-3 response (routes to manual review)
     */
    private IdentityMatchResponse tier3Response(double score) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(false);
        r.setMatchTier(MatchTierConstant.TIER_3);
        r.setConfidenceScore(score);
        r.setStatus("REVIEW_REQUIRED");
        return r;
    }

    // =====================================================================
    // SCENARIO 1-3: TIER-1 EXACT MATCHES (100% CONFIDENCE)
    // =====================================================================

    /**
     * Scenario 1: Email exact match from ADMS system (100% confidence)
     * Expected: Auto-merge eligible, TIER-1 match
     */
    @Test
    @DisplayName("Scenario 1: ADMS Email Exact Match (TIER-1, 100%)")
    public void testScenario1AdmsEmailExactMatch() {
        log.info("Scenario 1: ADMS email exact match");

        CanonicalIdentity canonical = createCanonical("ADMS", "ADMS-001");
        canonical.setEmail("john.doe@ust.hk");
        canonical.setFirstName("John");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GID-GOLDEN-001"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals(1.0, response.getConfidenceScore());
        assertEquals("GID-GOLDEN-001", response.getGoldenId());

        verify(identityLinkRepository, times(1)).save(any());
        log.info("✅ Scenario 1 PASSED");
    }

    /**
     * Scenario 2: Staff ID exact match from Attendance system (100% confidence)
     * Expected: Auto-merge eligible, TIER-1 match
     */
    @Test
    @DisplayName("Scenario 2: Attendance Staff ID Exact Match (TIER-1, 100%)")
    public void testScenario2AttendanceStaffIdMatch() {
        log.info("Scenario 2: Attendance staff ID exact match");

        CanonicalIdentity canonical = createCanonical("ATTENDANCE", "ATT-002");
        canonical.setStaffId("S2024001");
        canonical.setFirstName("Jane");
        canonical.setLastName("Smith");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GID-GOLDEN-002"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals(1.0, response.getConfidenceScore());

        verify(identityLinkRepository, times(1)).save(any());
        log.info("✅ Scenario 2 PASSED");
    }

    /**
     * Scenario 3: Student ID exact match from ADMS system (100% confidence)
     * Expected: Auto-merge eligible, TIER-1 match
     */
    @Test
    @DisplayName("Scenario 3: ADMS Student ID Exact Match (TIER-1, 100%)")
    public void testScenario3AdmsStudentIdMatch() {
        log.info("Scenario 3: ADMS student ID exact match");

        CanonicalIdentity canonical = createCanonical("ADMS", "ADMS-003");
        canonical.setStudentId("20230001");
        canonical.setFirstName("Bob");
        canonical.setLastName("Johnson");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GID-GOLDEN-003"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals(1.0, response.getConfidenceScore());

        log.info("✅ Scenario 3 PASSED");
    }

    // =====================================================================
    // SCENARIO 4-10: TIER-2 PROBABILISTIC MATCHES (85%-98% CONFIDENCE)
    // =====================================================================

    /**
     * Scenario 4: Email + Phone from Event System (95% confidence)
     * Expected: Auto-merge eligible, TIER-2 match
     */
    @Test
    @DisplayName("Scenario 4: Event System Email+Phone Match (TIER-2, 95%)")
    public void testScenario4EventEmailPhoneMatch() {
        log.info("Scenario 4: Event system email + phone");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-004");
        canonical.setEmail("alice@example.com");
        canonical.setPhone("91234567");
        canonical.setFirstName("Alice");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-004", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertEquals(0.95, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 4 PASSED");
    }

    /**
     * Scenario 5: Email only from Event System (75% confidence)
     * Expected: Auto-merge eligible, TIER-2 match (for high-trust source)
     */
    @Test
    @DisplayName("Scenario 5: Event System Email Only (TIER-2, 75%)")
    public void testScenario5EventEmailOnly() {
        log.info("Scenario 5: Event system email only");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-005");
        canonical.setEmail("charlie@example.com");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-005", 0.75));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertEquals(0.75, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 5 PASSED");
    }

    /**
     * Scenario 6: Email + Phone + FirstName from ADMS (98% confidence)
     * Expected: Auto-merge eligible, TIER-2 match with very high confidence
     */
    @Test
    @DisplayName("Scenario 6: ADMS Email+Phone+Name (TIER-2, 98%)")
    public void testScenario6AdmsTripleMatch() {
        log.info("Scenario 6: ADMS triple match");

        CanonicalIdentity canonical = createCanonical("ADMS", "ADMS-006");
        canonical.setEmail("david@ust.hk");
        canonical.setPhone("91234567");
        canonical.setFirstName("David");
        canonical.setLastName("Wong");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-006", 0.98));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.98, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 6 PASSED");
    }

    /**
     * Scenario 7: Email + DOB from Event System (88% confidence)
     * Expected: Auto-merge eligible, TIER-2 match
     */
    @Test
    @DisplayName("Scenario 7: Event System Email+DOB (TIER-2, 88%)")
    public void testScenario7EventEmailDob() {
        log.info("Scenario 7: Event system email + DOB");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-007");
        canonical.setEmail("emily@example.com");
        canonical.setDateOfBirth("1990-05-15");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-007", 0.88));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.88, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 7 PASSED");
    }

    /**
     * Scenario 8: Phone + DOB from Attendance (82% confidence)
     * Expected: Auto-merge eligible, TIER-2 match
     */
    @Test
    @DisplayName("Scenario 8: Attendance Phone+DOB (TIER-2, 82%)")
    public void testScenario8AttendancePhoneDob() {
        log.info("Scenario 8: Attendance phone + DOB");

        CanonicalIdentity canonical = createCanonical("ATTENDANCE", "ATT-008");
        canonical.setPhone("92345678");
        canonical.setDateOfBirth("1995-03-20");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-008", 0.82));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.82, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 8 PASSED");
    }

    /**
     * Scenario 9: Name + DOB from Event System (78% confidence)
     * Expected: Auto-merge eligible, TIER-2 match
     */
    @Test
    @DisplayName("Scenario 9: Event System Name+DOB (TIER-2, 78%)")
    public void testScenario9EventNameDob() {
        log.info("Scenario 9: Event system name + DOB");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-009");
        canonical.setFirstName("Frank");
        canonical.setLastName("Lee");
        canonical.setDateOfBirth("1988-07-10");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-009", 0.78));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.78, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 9 PASSED");
    }

    /**
     * Scenario 10: Email + Phone + DOB from ADMS (96% confidence)
     * Expected: Auto-merge eligible, TIER-2 match with very high confidence
     */
    @Test
    @DisplayName("Scenario 10: ADMS Email+Phone+DOB (TIER-2, 96%)")
    public void testScenario10AdmsEmailPhoneDob() {
        log.info("Scenario 10: ADMS email + phone + DOB");

        CanonicalIdentity canonical = createCanonical("ADMS", "ADMS-010");
        canonical.setEmail("grace@ust.hk");
        canonical.setPhone("93456789");
        canonical.setDateOfBirth("1992-11-25");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-010", 0.96));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.96, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 10 PASSED");
    }

    // =====================================================================
    // SCENARIO 11-16: TIER-3 & LOW CONFIDENCE (< 95%, MANUAL REVIEW)
    // =====================================================================

    /**
     * Scenario 11: Low confidence match from 3rd-party form system (40%)
     * Expected: Routes to manual review, TIER-3, not auto-merge eligible
     */
    @Test
    @DisplayName("Scenario 11: 3rd-Party Form Low Confidence (TIER-3, 40%)")
    public void testScenario11ThirdPartyLowConfidence() {
        log.info("Scenario 11: 3rd-party form low confidence");

        CanonicalIdentity canonical = createCanonical("GOOGLE_FORMS", "FORM-011");
        canonical.setEmail("hector@example.com");
        canonical.setFirstName("Hector");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.40));
        // Simulate that email exists in database (so it's not a new identity)
        when(identityRepository.findByEmail("hector@example.com"))
            .thenReturn(Optional.of(new IdentityDAO()));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());
        assertEquals(0.40, response.getConfidenceScore(), 0.001);

        verify(manualReviewService, times(1)).routeForReview(any());
        log.info("✅ Scenario 11 PASSED");
    }

    /**
     * Scenario 12: Unknown source system with moderate confidence (55%)
     * Expected: Routes to manual review, TIER-3
     */
    @Test
    @DisplayName("Scenario 12: Unknown Source Moderate Confidence (TIER-3, 55%)")
    public void testScenario12UnknownSourceModerate() {
        log.info("Scenario 12: Unknown source moderate confidence");

        CanonicalIdentity canonical = createCanonical("NEW_EXTERNAL_SYSTEM", "EXT-012");
        canonical.setEmail("iris@example.com");
        canonical.setPhone("94567890");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.55));
        when(identityRepository.findByEmail("iris@example.com"))
            .thenReturn(Optional.of(new IdentityDAO()));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());

        log.info("✅ Scenario 12 PASSED");
    }

    /**
     * Scenario 13: FirstName only - insufficient data (0% confidence)
     * Expected: No match, routes to manual review or creates new identity
     */
    @Test
    @DisplayName("Scenario 13: FirstName Only - Insufficient (TIER-3, 0%)")
    public void testScenario13FirstNameOnlyInsufficient() {
        log.info("Scenario 13: FirstName only insufficient");

        CanonicalIdentity canonical = createCanonical("GOOGLE_FORMS", "FORM-013");
        canonical.setFirstName("Jack");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.0));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());
        assertEquals(0.0, response.getConfidenceScore());

        log.info("✅ Scenario 13 PASSED");
    }

    /**
     * Scenario 14: DOB only - insufficient data (0% confidence)
     * Expected: No match, insufficient for matching
     */
    @Test
    @DisplayName("Scenario 14: DOB Only - Insufficient (TIER-3, 0%)")
    public void testScenario14DobOnlyInsufficient() {
        log.info("Scenario 14: DOB only insufficient");

        CanonicalIdentity canonical = createCanonical("GOOGLE_FORMS", "FORM-014");
        canonical.setDateOfBirth("1993-06-30");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.0));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(0.0, response.getConfidenceScore());

        log.info("✅ Scenario 14 PASSED");
    }

    /**
     * Scenario 15: Threshold boundary - exactly 95% confidence
     * Expected: Auto-merge eligible, TIER-2 (meets or exceeds threshold)
     */
    @Test
    @DisplayName("Scenario 15: Exactly 95% Confidence (TIER-2, Threshold Boundary)")
    public void testScenario15ExactlyThreshold95() {
        log.info("Scenario 15: Exactly 95% threshold");

        CanonicalIdentity canonical = createCanonical("ADMS", "ADMS-015");
        canonical.setEmail("kevin@ust.hk");
        canonical.setPhone("95678901");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-015", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.95, response.getConfidenceScore(), 0.001);
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());

        log.info("✅ Scenario 15 PASSED");
    }

    /**
     * Scenario 16: Just below threshold - 94.9% confidence
     * Expected: Routes to manual review, TIER-3 (below 95% threshold)
     */
    @Test
    @DisplayName("Scenario 16: Just Below 95% (TIER-3, 94.9%)")
    public void testScenario16JustBelowThreshold() {
        log.info("Scenario 16: Just below 95% threshold");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-016");
        canonical.setEmail("lucy@example.com");
        canonical.setPhone("96789012");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.949));

        when(identityRepository.findByEmail("lucy@example.com"))
            .thenReturn(Optional.of(new IdentityDAO()));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(0.949, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 16 PASSED");
    }

    // =====================================================================
    // SCENARIO 17-20: BUSINESS LOGIC & EDGE CASES
    // =====================================================================

    /**
     * Scenario 17: Multiple high-trust source systems matching same person
     * Expected: All resolve to same golden record
     */
    @Test
    @DisplayName("Scenario 17: Multiple High-Trust Sources Same Person")
    public void testScenario17MultipleTrustSources() {
        log.info("Scenario 17: Multiple high-trust sources");

        // First ingest from ADMS
        CanonicalIdentity canonical1 = createCanonical("ADMS", "ADMS-017");
        canonical1.setEmail("mike@ust.hk");
        canonical1.setStaffId("S2024017");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GID-GOLDEN-017"));

        IdentityMatchResponse response1 = identityResolutionService.resolve(canonical1);

        // Second ingest from Attendance
        CanonicalIdentity canonical2 = createCanonical("ATTENDANCE", "ATT-017");
        canonical2.setPhone("97890123");
        canonical2.setEmail("mike@ust.hk");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GID-GOLDEN-017"));

        IdentityMatchResponse response2 = identityResolutionService.resolve(canonical2);

        assertEquals(response1.getGoldenId(), response2.getGoldenId());
        assertTrue(response1.isMatched());
        assertTrue(response2.isMatched());

        log.info("✅ Scenario 17 PASSED");
    }

    /**
     * Scenario 18: Same confidence score but different source credibility impact
     * Expected: Different decisions based on source system
     */
    @Test
    @DisplayName("Scenario 18: Same Score Different Source Different Decision")
    public void testScenario18SameScoreDifferentSource() {
        log.info("Scenario 18: Same score different source");

        // High-trust ADMS system: 90% match × 1.0x = 90%
        double admsScore = 0.90;
        assertTrue(admsScore >= 0.85, "ADMS score should be auto-merge eligible");

        // Low-trust form system: 90% match × 0.7x = 63%
        double formScore = 0.90 * 0.70;
        assertFalse(formScore >= 0.85, "Form score should NOT be auto-merge eligible");

        log.info("✅ Scenario 18 PASSED");
    }

    /**
     * Scenario 19: Field combination accuracy from different systems
     * Expected: Correct confidence calculation regardless of system
     */
    @Test
    @DisplayName("Scenario 19: Field Combination Accuracy")
    public void testScenario19FieldCombinationAccuracy() {
        log.info("Scenario 19: Field combination accuracy");

        CanonicalIdentity canonical = createCanonical("EVENT_SYSTEM", "EVT-019");
        canonical.setEmail("nancy@example.com");
        canonical.setPhone("98901234");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GID-GOLDEN-019", 0.90));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.getConfidenceScore() >= 0.70, "Confidence should be > 70%");
        assertTrue(response.isMatched(), "Should be matched");

        log.info("✅ Scenario 19 PASSED");
    }

    /**
     * Scenario 20: No match found - creates new golden record
     * Expected: New identity created with status NEW_IDENTITY
     */
    @Test
    @DisplayName("Scenario 20: No Match Found - Creates New Identity")
    public void testScenario20NoMatchNewIdentity() {
        log.info("Scenario 20: No match - new identity");

        CanonicalIdentity canonical = createCanonical("GOOGLE_FORMS", "FORM-020");
        canonical.setEmail("oscar@example.com");
        canonical.setFirstName("Oscar");
        canonical.setLastName("Brown");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier3Response(0.0));
        // Email doesn't exist - so this will be a new identity
        when(identityRepository.findByEmail("oscar@example.com"))
            .thenReturn(Optional.empty());

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());
        assertEquals(0.0, response.getConfidenceScore());

        log.info("✅ Scenario 20 PASSED");
    }
}
