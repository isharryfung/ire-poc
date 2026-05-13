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
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.dto.IdentityMatchRequest;
import org.hkust.ire.dto.IdentityMatchResponse;

/**
 * Mock-based unit tests for Identity Resolution Service
 * 
 * Tests TIER-1, TIER-2, and TIER-3 matching logic without database
 * 
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Resolution Service - Mock Tests")
public class IdentityResolutionServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolutionServiceMockTest.class);

    @Mock
    private IdentityRepository identityRepository;

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
     * Test TIER-2: 95% confidence - Email + Mobile match
     */
    @Test
    @DisplayName("TIER-2: 95% Confidence - Email + Mobile Match")
    public void testTier2EmailMobileMatch() {
        log.info("Test: TIER-2 Email + Mobile match (95%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setMobile("98765432");
        request.setSource("EVENT_SYSTEM");

        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(2L);
        mockIdentity.setEmail("john@example.com");
        mockIdentity.setMobile("98765432");

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_MOBILE", 0.95, mockIdentity));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        assertNotNull(response);
        assertEquals(0.95, response.getConfidence(), "Confidence should be 95%");
        assertEquals("TIER_2_MATCH", response.getMatchTier());
        assertTrue(response.isAutoMergeEligible(), "95% should be auto-merge eligible");

        log.info("✅ TIER-2 Email + Mobile test PASSED");
    }

    /**
     * Test TIER-2: 90% confidence - Email + Name match
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
        mockIdentity.setId(3L);
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
     * Test TIER-3: Source Credibility Impact - 90% × 0.8x = 72%
     */
    @Test
    @DisplayName("TIER-3: Source Credibility Impact - 90% × 0.8x = 72%")
    public void testTier3SourceCredibilityImpact() {
        log.info("Test: TIER-3 Source credibility impact (72%)");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("User Doe");
        request.setSource("GOOGLE_FORMS");  // Low trust: 0.8x

        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_EMAIL_NAME", 0.90, null));

        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // After credibility multiplier: 90% × 0.8 = 72%
        double expectedConfidence = 0.90 * 0.80;
        assertEquals(expectedConfidence, response.getConfidence(), 0.01);
        assertEquals(0.72, response.getConfidence());
        
        assertFalse(response.isAutoMergeEligible());
        assertEquals("TIER_3_MANUAL_REVIEW", response.getMatchTier());

        log.info("✅ TIER-3 Source credibility test PASSED (90% × 0.8 = 72%)");
    }

    /**
     * Test: No Match Found
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
