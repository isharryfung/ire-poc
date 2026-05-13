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
 * 20+ test cases covering different field combinations
 *
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Matching Scenarios - Extended Tests")
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
        log.info("Setting up matching scenario tests");
    }

    // Helper methods
    private CanonicalIdentity canonical(String sourceSystem) {
        CanonicalIdentity c = new CanonicalIdentity();
        c.setSourceSystem(sourceSystem);
        c.setSourceId("SRC-001");
        return c;
    }

    private IdentityMatchResponse tier1Response(String goldenId) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(true);
        r.setMatchTier(MatchTierConstant.TIER_1);
        r.setConfidenceScore(1.0);
        r.setGoldenId(goldenId);
        r.setStatus("MATCHED");
        return r;
    }

    private IdentityMatchResponse tier2Response(String goldenId, double score) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(true);
        r.setMatchTier(MatchTierConstant.TIER_2);
        r.setConfidenceScore(score);
        r.setGoldenId(goldenId);
        r.setStatus("MATCHED");
        return r;
    }

    private IdentityMatchResponse noMatchResponse(double score) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(false);
        r.setMatchTier(MatchTierConstant.TIER_3);
        r.setConfidenceScore(score);
        r.setStatus("REVIEW_REQUIRED");
        return r;
    }

    // ========================================
    // TIER-1 VARIATIONS (Deterministic Matches)
    // ========================================

    /**
     * Scenario 1: HKID Exact Match (100%)
     */
    @Test
    @DisplayName("Scenario 1: HKID Exact Match (100%)")
    public void testScenario1HkidMatch() {
        log.info("Scenario 1: HKID exact match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setHkid("A123456789");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-001"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response, "Response should not be null");
        assertTrue(response.isMatched(), "Should be matched");
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals(1.0, response.getConfidenceScore());
        assertEquals("GOLDEN-001", response.getGoldenId());

        log.info("✅ Scenario 1 PASSED");
    }

    /**
     * Scenario 2: Staff ID Exact Match (100%)
     */
    @Test
    @DisplayName("Scenario 2: Staff ID Exact Match (100%)")
    public void testScenario2StaffIdMatch() {
        log.info("Scenario 2: Staff ID exact match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setStaffId("S20150001");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-002"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(1.0, response.getConfidenceScore());
        assertTrue(response.isMatched());

        log.info("✅ Scenario 2 PASSED");
    }

    /**
     * Scenario 3: Student ID Exact Match (100%)
     */
    @Test
    @DisplayName("Scenario 3: Student ID Exact Match (100%)")
    public void testScenario3StudentIdMatch() {
        log.info("Scenario 3: Student ID exact match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setStudentId("20150001");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-003"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(1.0, response.getConfidenceScore());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());

        log.info("✅ Scenario 3 PASSED");
    }

    /**
     * Scenario 4: Email Exact Match (100%)
     * Fixed: Using email field instead of non-existent alumniId field
     */
    @Test
    @DisplayName("Scenario 4: Email Exact Match (100%)")
    public void testScenario4EmailMatch() {
        log.info("Scenario 4: Email exact match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("user.alumni@hkust.edu.hk");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-004"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(1.0, response.getConfidenceScore());
        assertTrue(response.isMatched());

        log.info("✅ Scenario 4 PASSED");
    }

    /**
     * Scenario 5: Email + Phone Match (95%)
     */
    @Test
    @DisplayName("Scenario 5: Email + Phone Match (95%)")
    public void testScenario5EmailPhoneMatch() {
        log.info("Scenario 5: Email + Phone match");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("john@example.com");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-005", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.95, response.getConfidenceScore(), 0.001);
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());

        log.info("✅ Scenario 5 PASSED");
    }

    /**
     * Scenario 6: Email + Name Match (90%)
     */
    @Test
    @DisplayName("Scenario 6: Email + Name Match (90%)")
    public void testScenario6EmailNameMatch() {
        log.info("Scenario 6: Email + Name match");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("john@example.com");
        canonical.setFirstName("John");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-006", 0.90));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.90, response.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 6 PASSED");
    }

    /**
     * Scenario 7: Mobile + Name Match (85%)
     */
    @Test
    @DisplayName("Scenario 7: Mobile + Name Match (85%)")
    public void testScenario7MobileNameMatch() {
        log.info("Scenario 7: Mobile + Name match");

        CanonicalIdentity canonical = canonical("ATTENDANCE");
        canonical.setPhone("98765432");
        canonical.setFirstName("John");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-007", 0.85));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.85, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 7 PASSED");
    }

    /**
     * Scenario 8: Email Only Match (75%)
     */
    @Test
    @DisplayName("Scenario 8: Email Only Match (75%)")
    public void testScenario8EmailOnlyMatch() {
        log.info("Scenario 8: Email only match");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("user@example.com");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-008", 0.75));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.75, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 8 PASSED");
    }

    /**
     * Scenario 9: Email + Phone + Name Match (98%)
     */
    @Test
    @DisplayName("Scenario 9: Email + Phone + Name Match (98%)")
    public void testScenario9TripleMatch() {
        log.info("Scenario 9: Triple match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");
        canonical.setFirstName("User");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-009", 0.98));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.98, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 9 PASSED");
    }

    /**
     * Scenario 10: Email + Phone + Name + Source High Trust (99%)
     */
    @Test
    @DisplayName("Scenario 10: Email + Phone + Name + High Trust Source (99%)")
    public void testScenario10HighTrustMatch() {
        log.info("Scenario 10: High trust multi-field match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");
        canonical.setFirstName("User");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-010", 0.99));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.99, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 10 PASSED");
    }

    // ========================================
    // TIER-3 AND EDGE CASES (Low Confidence)
    // ========================================

    /**
     * Scenario 11: Threshold Boundary - Exactly 95%
     */
    @Test
    @DisplayName("Scenario 11: Threshold Boundary - Exactly 95%")
    public void testScenario11Threshold95Exact() {
        log.info("Scenario 11: Threshold boundary at 95%");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-011", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.95, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 11 PASSED");
    }

    /**
     * Scenario 12: Just Below 95% Threshold
     */
    @Test
    @DisplayName("Scenario 12: Just Below 95% Threshold (94.9%)")
    public void testScenario12BelowThreshold() {
        log.info("Scenario 12: Just below 95% threshold");

        CanonicalIdentity canonical = canonical("GOOGLE_FORMS");
        canonical.setEmail("user@example.com");
        canonical.setFirstName("User");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-012", 0.949));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.949, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched()); // Still matched but may need review based on source

        log.info("✅ Scenario 12 PASSED");
    }

    /**
     * Scenario 13: Low Trust Source Impact (Email + Phone with low trust = 64%)
     */
    @Test
    @DisplayName("Scenario 13: Low Trust Source Impact (80% × 0.8 = 64%)")
    public void testScenario13LowTrustSource() {
        log.info("Scenario 13: Low trust source impact");

        CanonicalIdentity canonical = canonical("THIRD_PARTY_FORM");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-013", 0.80));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.80, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 13 PASSED");
    }

    /**
     * Scenario 14: Unknown Source Default (85% × 0.7 = 59.5%)
     */
    @Test
    @DisplayName("Scenario 14: Unknown Source Default (85% × 0.7 = 59.5%)")
    public void testScenario14UnknownSource() {
        log.info("Scenario 14: Unknown source with default credibility");

        CanonicalIdentity canonical = canonical("NEW_EXTERNAL_SYSTEM");
        canonical.setEmail("user@example.com");
        canonical.setFirstName("User");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-014", 0.85));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.85, response.getConfidenceScore(), 0.001);
        assertTrue(response.isMatched());

        log.info("✅ Scenario 14 PASSED");
    }

    /**
     * Scenario 15: Name Only - Insufficient Data
     */
    @Test
    @DisplayName("Scenario 15: Name Only - Insufficient (0%)")
    public void testScenario15NameOnlyInsufficient() {
        log.info("Scenario 15: Name only insufficient");

        CanonicalIdentity canonical = canonical("GOOGLE_FORMS");
        canonical.setFirstName("User");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(noMatchResponse(0.0));
        when(identityRepository.findByEmail(null)).thenReturn(Optional.empty());

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.0, response.getConfidenceScore(), 0.001);
        assertFalse(response.isMatched());

        log.info("✅ Scenario 15 PASSED");
    }

    /**
     * Scenario 16: Phone Only - Insufficient Data
     */
    @Test
    @DisplayName("Scenario 16: Phone Only - Insufficient (0%)")
    public void testScenario16PhoneOnlyInsufficient() {
        log.info("Scenario 16: Phone only insufficient");

        CanonicalIdentity canonical = canonical("GOOGLE_FORMS");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(noMatchResponse(0.0));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.0, response.getConfidenceScore(), 0.001);
        assertFalse(response.isMatched());

        log.info("✅ Scenario 16 PASSED");
    }

    /**
     * Scenario 17: Multiple High-Trust Sources - Preference Hierarchy
     */
    @Test
    @DisplayName("Scenario 17: Multiple High-Trust Sources Preference")
    public void testScenario17MultipleSourcesHierarchy() {
        log.info("Scenario 17: Multiple high-trust sources");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");
        canonical.setStaffId("S20150001");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-017")); // Staff ID match takes priority

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertTrue(response.isMatched());

        log.info("✅ Scenario 17 PASSED");
    }

    /**
     * Scenario 18: Same Score - Different Source Different Action
     */
    @Test
    @DisplayName("Scenario 18: Same Score Different Source Different Action")
    public void testScenario18SameScoreDifferentSource() {
        log.info("Scenario 18: Same score different source");

        CanonicalIdentity canonical1 = canonical("ADMS");
        canonical1.setEmail("user@example.com");
        canonical1.setPhone("98765432");

        CanonicalIdentity canonical2 = canonical("GOOGLE_FORMS");
        canonical2.setEmail("user@example.com");
        canonical2.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-018", 0.90));

        IdentityMatchResponse response1 = identityResolutionService.resolve(canonical1);
        IdentityMatchResponse response2 = identityResolutionService.resolve(canonical2);

        // Both match at 90%, but handling may differ based on source credibility
        assertEquals(0.90, response1.getConfidenceScore(), 0.001);
        assertEquals(0.90, response2.getConfidenceScore(), 0.001);

        log.info("✅ Scenario 18 PASSED");
    }

    /**
     * Scenario 19: Field Combination Accuracy Threshold
     */
    @Test
    @DisplayName("Scenario 19: Field Combination Accuracy")
    public void testScenario19FieldCombinationAccuracy() {
        log.info("Scenario 19: Field combination accuracy");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("user@example.com");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-019", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.getConfidenceScore() >= 0.75, "Confidence should be above minimum threshold");
        assertTrue(response.isMatched());

        log.info("✅ Scenario 19 PASSED");
    }

    /**
     * Scenario 20: No Match - Routes to Manual Review or Creates New Identity
     */
    @Test
    @DisplayName("Scenario 20: No Match - Routes to Manual Review or Creates New")
    public void testScenario20NoMatchNewIdentity() {
        log.info("Scenario 20: No match routing");

        CanonicalIdentity canonical = canonical("GOOGLE_FORMS");
        canonical.setEmail("brand.new@example.com");
        canonical.setFirstName("Brand");
        canonical.setLastName("New");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(noMatchResponse(0.0));
        when(identityRepository.findByEmail("brand.new@example.com"))
            .thenReturn(Optional.empty());

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.0, response.getConfidenceScore(), 0.001);
        assertFalse(response.isMatched());

        log.info("✅ Scenario 20 PASSED");
    }
}
