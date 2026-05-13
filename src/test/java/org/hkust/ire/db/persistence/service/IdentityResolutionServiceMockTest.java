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
 * Mock-based unit tests for Identity Resolution Service
 *
 * Tests TIER-1, TIER-2, and TIER-3 orchestration logic without database.
 *
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Resolution Service - Mock Tests")
public class IdentityResolutionServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolutionServiceMockTest.class);

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
        log.info("Setting up mock tests for Identity Resolution Service");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * Test TIER-1: Exact match on HKID (100% confidence)
     */
    @Test
    @DisplayName("TIER-1: 100% Confidence - HKID Exact Match")
    public void testTier1HkidExactMatch() {
        log.info("Test: TIER-1 HKID exact match");

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setHkid("A123456789");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier1Response("GOLDEN-001"));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response, "Response should not be null");
        assertTrue(response.isMatched(), "Should be matched");
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier(), "Match tier should be TIER_1");
        assertEquals(1.0, response.getConfidenceScore(), "Confidence should be 100%");
        assertEquals("GOLDEN-001", response.getGoldenId(), "Golden ID should match");

        log.info("TIER-1 HKID test PASSED");
    }

    /**
     * Test TIER-2: High-confidence probabilistic match
     */
    @Test
    @DisplayName("TIER-2: 95% Confidence - Email + Phone Match")
    public void testTier2EmailPhoneMatch() {
        log.info("Test: TIER-2 Email + Phone match (95%)");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("john@example.com");
        canonical.setPhone("98765432");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-002", 0.95));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertEquals(0.95, response.getConfidenceScore(), 0.001);

        log.info("TIER-2 Email + Phone test PASSED");
    }

    /**
     * Test TIER-2: Email + Name match (90% score)
     */
    @Test
    @DisplayName("TIER-2: 90% Confidence - Email + Name Match")
    public void testTier2EmailNameMatch() {
        log.info("Test: TIER-2 Email + Name match (90%)");

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("john@example.com");
        canonical.setFirstName("John");
        canonical.setLastName("Doe");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(tier2Response("GOLDEN-003", 0.90));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.90, response.getConfidenceScore(), 0.001);

        log.info("TIER-2 Email + Name test PASSED");
    }

    /**
     * Test TIER-3: Low confidence - routes to manual review
     */
    @Test
    @DisplayName("TIER-3: Low Confidence - Routes to Manual Review")
    public void testTier3RoutesToManualReview() {
        log.info("Test: TIER-3 low confidence routing");

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setEmail("user@example.com");
        canonical.setFirstName("User");

        when(matchingEngineService.match(any(CanonicalIdentity.class)))
            .thenReturn(noMatchResponse(0.40));
        // isNewIdentity() calls findByEmail; returning a present Optional makes it return false
        // so the code takes the routeToManualReview path (not createNewGoldenRecord)
        when(identityRepository.findByEmail("user@example.com"))
            .thenReturn(Optional.of(new IdentityDAO()));

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());

        log.info("TIER-3 manual review routing test PASSED");
    }

    /**
     * Test: No Match Found - new identity path
     */
    @Test
    @DisplayName("No Match Found - New Identity Created")
    public void testNoMatchCreateNew() {
        log.info("Test: No match found - new identity");

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setEmail("newuser@example.com");
        canonical.setFirstName("New");
        canonical.setLastName("User");

        IdentityMatchResponse noMatch = noMatchResponse(0.0);
        when(matchingEngineService.match(any(CanonicalIdentity.class))).thenReturn(noMatch);

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertFalse(response.isMatched());

        log.info("No match test PASSED");
    }
}
