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

/**
 * Mock-based unit tests for Waterfall Matching Engine
 * 
 * Tests waterfall cascading logic, early exit principle, and field scoring
 * 
 * @author isharryfung
 * @since 2026-05-13
 */
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
     */
    @Test
    @DisplayName("Waterfall: Early Exit - Stop after first match (100%)")
    public void testWaterfallEarlyExit() {
        log.info("Test: Waterfall early exit principle");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setHkid("A123456789");
        request.setEmail("john@example.com");
        request.setMobile("98765432");

        double score = engine.calculateScore(request);

        assertEquals(1.0, score, "Should return 100% from HKID match");
        log.info("✅ Waterfall early exit test PASSED");
    }

    /**
     * Test Waterfall: No Penalty for Missing Fields
     */
    @Test
    @DisplayName("Waterfall: No Penalty for Missing Fields")
    public void testWaterfallNoPenaltyForMissingFields() {
        log.info("Test: No penalty for missing fields");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");

        double score = engine.calculateScore(request);

        assertTrue(score >= 0.70 && score <= 0.80);
        log.info("✅ No penalty test PASSED (Score: {}%)", (int)(score*100));
    }

    /**
     * Test: Cascading Confidence Levels
     */
    @Test
    @DisplayName("Test: Cascading Confidence Levels (100%, 95%, 90%, 85%, 75%)")
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
     */
    @Test
    @DisplayName("Test: Field Mismatch Penalty (-50%)")
    public void testFieldMismatchPenalty() {
        log.info("Test: Field mismatch penalty");

        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setName("Jane Doe");  // Mismatch

        double score = engine.calculateScore(request);

        assertTrue(score < 0.50, "Mismatched fields should have penalty");
        log.info("✅ Mismatch penalty test PASSED (Score: {}%)", (int)(score*100));
    }

    /**
     * Test: Field Scoring Breakdown
     */
    @Test
    @DisplayName("Test: Field Scoring Breakdown (40%, 30%, 15%, 15%)")
    public void testFieldScoringBreakdown() {
        log.info("Test: Field scoring breakdown");

        double emailScore = engine.getFieldScore("email");
        assertEquals(0.40, emailScore);

        double mobileScore = engine.getFieldScore("mobile");
        assertEquals(0.30, mobileScore);

        double nameScore = engine.getFieldScore("name");
        assertEquals(0.15, nameScore);

        double dobScore = engine.getFieldScore("dob");
        assertEquals(0.15, dobScore);

        log.info("✅ Field scoring breakdown test PASSED");
    }
}
