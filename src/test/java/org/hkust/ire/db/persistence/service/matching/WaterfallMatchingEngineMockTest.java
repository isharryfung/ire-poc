package org.hkust.ire.db.persistence.service.matching;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;

/**
 * Mock-based unit tests for Waterfall Matching Engine
 *
 * Tests waterfall cascading logic and early-exit principle.
 *
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Waterfall Matching Engine - Mock Tests")
public class WaterfallMatchingEngineMockTest {

    private static final Logger log = LoggerFactory.getLogger(WaterfallMatchingEngineMockTest.class);

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private ConfidenceCalculator confidenceCalculator;

    @InjectMocks
    private WaterfallMatchingEngine engine;

    @BeforeEach
    public void setUp() {
        log.info("Setting up Waterfall Matching Engine tests");
        ReflectionTestUtils.setField(engine, "tier2CandidateLimit", 500);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private IdentityDAO mockIdentity(String goldenId, String email) {
        IdentityDAO dao = new IdentityDAO();
        dao.setGoldenId(goldenId);
        dao.setEmail(email);
        return dao;
    }

    private CanonicalIdentity withHkid(String hkid) {
        CanonicalIdentity c = new CanonicalIdentity();
        c.setSourceSystem("ADMS");
        c.setHkid(hkid);
        return c;
    }

    private CanonicalIdentity withEmail(String email) {
        CanonicalIdentity c = new CanonicalIdentity();
        c.setSourceSystem("EVENT_SYSTEM");
        c.setEmail(email);
        return c;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * Test Waterfall Principle: Early Exit on TIER-1 HKID match
     */
    @Test
    @DisplayName("Waterfall: Early Exit - TIER-1 match stops cascade")
    public void testWaterfallEarlyExit() {
        log.info("Test: Waterfall early exit principle");

        IdentityDAO existing = mockIdentity("GOLDEN-001", "john@example.com");
        when(identityRepository.findByHkid("A123456789")).thenReturn(Optional.of(existing));

        CanonicalIdentity request = withHkid("A123456789");
        request.setEmail("john@example.com");
        request.setPhone("98765432");

        IdentityMatchResponse response = engine.match(request);

        assertNotNull(response);
        assertTrue(response.isMatched(), "Should be matched");
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier(), "Should return TIER_1");
        assertEquals(1.0, response.getConfidenceScore(), "Confidence should be 100%");
        assertEquals("GOLDEN-001", response.getGoldenId());

        // Ensure Tier-2 (DB scan) was never triggered
        verify(identityRepository, never()).findByStatus(any(), any());

        log.info("Waterfall early exit test PASSED");
    }

    /**
     * Test: TIER-2 match via confidence calculator when TIER-1 finds nothing
     */
    @Test
    @DisplayName("Waterfall: TIER-2 Match - Email probabilistic match")
    public void testTier2Match() {
        log.info("Test: TIER-2 probabilistic match");

        IdentityDAO candidate = mockIdentity("GOLDEN-002", "user@example.com");

        when(identityRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(identityRepository.findByStatus(eq("ACTIVE"), any()))
            .thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate)))
            .thenReturn(0.80);

        CanonicalIdentity request = withEmail("user@example.com");

        IdentityMatchResponse response = engine.match(request);

        assertNotNull(response);
        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertTrue(response.getConfidenceScore() >= 0.70, "Score should exceed TIER_2_THRESHOLD");

        log.info("TIER-2 match test PASSED (score={})", response.getConfidenceScore());
    }

    /**
     * Test: No match when confidence is too low
     */
    @Test
    @DisplayName("No Match: Low confidence score below threshold")
    public void testNoMatchLowConfidence() {
        log.info("Test: No match - low confidence");

        IdentityDAO candidate = mockIdentity("GOLDEN-999", "other@example.com");

        when(identityRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(identityRepository.findByStatus(eq("ACTIVE"), any()))
            .thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), any()))
            .thenReturn(0.20);

        CanonicalIdentity request = withEmail("new@example.com");

        IdentityMatchResponse response = engine.match(request);

        assertNotNull(response);
        assertFalse(response.isMatched(), "Should not match with low confidence");
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());

        log.info("No match / low confidence test PASSED");
    }

    /**
     * Test: Empty candidate list routes to TIER-3
     */
    @Test
    @DisplayName("Waterfall: Empty candidates → TIER-3")
    public void testEmptyCandidatesNoMatch() {
        log.info("Test: Empty candidate pool");

        when(identityRepository.findByHkid(any())).thenReturn(Optional.empty());
        when(identityRepository.findByStaffId(any())).thenReturn(Optional.empty());
        when(identityRepository.findByStudentId(any())).thenReturn(Optional.empty());
        when(identityRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(identityRepository.findByStatus(eq("ACTIVE"), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        CanonicalIdentity request = new CanonicalIdentity();
        request.setSourceSystem("THIRD_PARTY");
        request.setFirstName("Ghost");

        IdentityMatchResponse response = engine.match(request);

        assertNotNull(response);
        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());

        log.info("Empty candidates test PASSED");
    }

    /**
     * Test: TIER-1 match on StaffId
     */
    @Test
    @DisplayName("TIER-1: Exact match on StaffId")
    public void testTier1StaffIdMatch() {
        log.info("Test: TIER-1 StaffId match");

        IdentityDAO existing = mockIdentity("GOLDEN-STAFF-001", "staff@hkust.edu.hk");
        when(identityRepository.findByHkid(any())).thenReturn(Optional.empty());
        when(identityRepository.findByStaffId("STAFF20150001")).thenReturn(Optional.of(existing));

        CanonicalIdentity request = new CanonicalIdentity();
        request.setSourceSystem("ADMS");
        request.setStaffId("STAFF20150001");

        IdentityMatchResponse response = engine.match(request);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals(1.0, response.getConfidenceScore());
        assertEquals("GOLDEN-STAFF-001", response.getGoldenId());

        log.info("TIER-1 StaffId test PASSED");
    }
}
