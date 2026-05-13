package org.hkust.ire.db.persistence.service.matching;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.common.constant.SourceSystemConstant;
import org.hkust.ire.db.persistence.repository.SourceCredibilityRepository;

/**
 * Mock-based unit tests for Source Credibility Scorer
 *
 * Tests source trust multipliers and impact on match confidence.
 * Credibility weights: CRM=1.0, ADMS/Attendance=0.9, Third-party=0.8 (defaults)
 *
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Source Credibility Scorer - Mock Tests")
public class SourceCredibilityScorerMockTest {

    private static final Logger log = LoggerFactory.getLogger(SourceCredibilityScorerMockTest.class);

    @Mock
    private SourceCredibilityRepository sourceCredibilityRepository;

    @InjectMocks
    private SourceCredibilityScorer scorer;

    @BeforeEach
    public void setUp() {
        log.info("Setting up Source Credibility Scorer tests");
        // No DB record found → fall through to default credibility logic
        when(sourceCredibilityRepository.findBySourceSystem(anyString())).thenReturn(Optional.empty());
    }

    /**
     * Test: High Trust Source - ADMS (0.9x multiplier per SourceSystemConstant)
     */
    @Test
    @DisplayName("High Trust Source (0.9x): ADMS")
    public void testHighTrustAdms() {
        log.info("Test: High trust ADMS source");

        double multiplier = scorer.score("ADMS");

        assertEquals(SourceSystemConstant.CREDIBILITY_ADMS, multiplier, 0.001);
        log.info("ADMS credibility test PASSED ({})", multiplier);
    }

    /**
     * Test: High Trust Source - Attendance System (0.9x multiplier)
     */
    @Test
    @DisplayName("High Trust Source (0.9x): Attendance System")
    public void testHighTrustAttendance() {
        log.info("Test: High trust Attendance source");

        double multiplier = scorer.score("ATTENDANCE");

        assertEquals(SourceSystemConstant.CREDIBILITY_ADMS, multiplier, 0.001);
        log.info("Attendance credibility test PASSED ({})", multiplier);
    }

    /**
     * Test: Third-party Source - Google Forms (0.8x multiplier)
     */
    @Test
    @DisplayName("Third-Party Source (0.8x): Google Forms")
    public void testThirdPartyGoogleForms() {
        log.info("Test: Third-party Google Forms source");

        double multiplier = scorer.score("GOOGLE_FORMS");

        assertEquals(SourceSystemConstant.CREDIBILITY_THIRD_PARTY, multiplier, 0.001);
        log.info("Google Forms credibility test PASSED ({})", multiplier);
    }

    /**
     * Test: Third-Party Ticketing (0.8x multiplier)
     */
    @Test
    @DisplayName("Third-Party Source (0.8x): Third-Party Ticketing")
    public void testThirdPartyTicketing() {
        log.info("Test: Third-party ticketing source");

        double multiplier = scorer.score("THIRD_PARTY_TICKETING");

        assertEquals(SourceSystemConstant.CREDIBILITY_THIRD_PARTY, multiplier, 0.001);
        log.info("Third-party ticketing credibility test PASSED ({})", multiplier);
    }

    /**
     * Test: Unknown Source (0.8x multiplier - defaults to THIRD_PARTY)
     */
    @Test
    @DisplayName("Unknown Source (0.8x): Default Multiplier")
    public void testUnknownSourceDefault() {
        log.info("Test: Unknown source default multiplier");

        double multiplier = scorer.score("UNKNOWN_SOURCE");

        assertEquals(SourceSystemConstant.CREDIBILITY_THIRD_PARTY, multiplier, 0.001);
        log.info("Unknown source credibility test PASSED ({})", multiplier);
    }

    /**
     * Test: Real-World Scenario - Google Forms Impact
     *
     * 90% base match × 0.8x (THIRD_PARTY) = 72%
     * Result: Below AUTO_MERGE_THRESHOLD (0.85) → routes to manual review
     */
    @Test
    @DisplayName("Real-World: 90% Base × 0.8x (Google Forms) = 72%")
    public void testRealWorldGoogleFormsScenario() {
        log.info("Test: Real-world Google Forms scenario");

        double baseScore = 0.90;
        double multiplier = scorer.score("GOOGLE_FORMS");
        double finalScore = baseScore * multiplier;

        log.info("Base: {}%, Multiplier: {}x, Final: {}%",
            (int)(baseScore*100), multiplier, Math.round(finalScore*100));

        assertEquals(SourceSystemConstant.CREDIBILITY_THIRD_PARTY, multiplier, 0.001);
        assertTrue(finalScore < MatchTierConstant.AUTO_MERGE_THRESHOLD,
                "Should NOT be auto-mergeable; final score=" + finalScore);

        log.info("Real-world Google Forms test PASSED");
    }

    /**
     * Test: Real-World Scenario - ADMS Impact
     *
     * 95% base match × 0.9x (ADMS) = 85.5%
     */
    @Test
    @DisplayName("Real-World: 95% Base × 0.9x (ADMS) = 85.5%")
    public void testRealWorldAdmsScenario() {
        log.info("Test: Real-world ADMS scenario");

        double baseScore = 0.95;
        double multiplier = scorer.score("ADMS");
        double finalScore = baseScore * multiplier;

        log.info("Base: {}%, Multiplier: {}x, Final: {}%",
            (int)(baseScore*100), multiplier, Math.round(finalScore*100));

        assertEquals(SourceSystemConstant.CREDIBILITY_ADMS, multiplier, 0.001);
        assertTrue(finalScore > MatchTierConstant.TIER_2_THRESHOLD, "Should exceed TIER_2_THRESHOLD");

        log.info("Real-world ADMS test PASSED");
    }

    /**
     * Test: Source Credibility Prevents False Merges
     *
     * 90% base × ADMS(0.9) = 81% — exceeds TIER_2_THRESHOLD
     * 90% base × THIRD_PARTY(0.8) = 72% — below AUTO_MERGE_THRESHOLD
     */
    @Test
    @DisplayName("Source Credibility Prevents False Merges")
    public void testSourceCredibilityPreventsFalseMerges() {
        log.info("Test: Source credibility prevents false merges");

        double matchScore = 0.90;

        double admsFinalScore = matchScore * scorer.score("ADMS");
        double thirdPartyFinalScore = matchScore * scorer.score("GOOGLE_FORMS");

        assertTrue(admsFinalScore > MatchTierConstant.TIER_2_THRESHOLD, "ADMS 90% should exceed TIER_2_THRESHOLD");
        assertFalse(thirdPartyFinalScore >= MatchTierConstant.AUTO_MERGE_THRESHOLD,
                "Google Forms should NOT reach auto-merge threshold");

        log.info("False merge prevention test PASSED");
        log.info("  - ADMS: {}% → qualifies for TIER_2", Math.round(admsFinalScore*100));
        log.info("  - Google Forms: {}% → degraded by low credibility", Math.round(thirdPartyFinalScore*100));
    }
}
