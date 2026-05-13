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

/**
 * Mock-based unit tests for Source Credibility Scorer
 * 
 * Tests source trust multipliers and impact on match confidence
 * 
 * @author isharryfung
 * @since 2026-05-13
 */
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
     * 90% base match × 0.8x (Google Forms) = 72%
     * Result: Routes to TIER-3 manual review
     */
    @Test
    @DisplayName("Real-World: 90% Base × 0.8x (Google Forms) = 72%")
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
     * 95% base match × 1.0x (ADMS) = 95%
     * Result: Auto-mergeable
     */
    @Test
    @DisplayName("Real-World: 95% Base × 1.0x (ADMS) = 95%")
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
     */
    @Test
    @DisplayName("Source Credibility Prevents False Merges")
    public void testSourceCredibilityPreventsFalseMerges() {
        log.info("Test: Source credibility prevents false merges");

        double matchScore = 0.90;

        double admsFinalScore = matchScore * scorer.getCredibilityMultiplier("ADMS");
        assertTrue(admsFinalScore >= 0.95, "ADMS should be auto-mergeable");

        double formsFinalScore = matchScore * scorer.getCredibilityMultiplier("GOOGLE_FORMS");
        assertFalse(formsFinalScore >= 0.95, "Google Forms should NOT be auto-mergeable");

        log.info("✅ False merge prevention test PASSED");
        log.info("  - ADMS: {}% → Auto-merge", (int)(admsFinalScore*100));
        log.info("  - Google Forms: {}% → Manual review", (int)(formsFinalScore*100));
    }
}
