package org.hkust.ire.db.persistence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.IdentityLinkRepository;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.identity.IdentityCacheService;
import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.db.persistence.service.matching.ConfidenceCalculator;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.db.persistence.service.matching.SourceCredibilityScorer;
import org.hkust.ire.db.persistence.service.matching.WaterfallMatchingEngine;
import org.hkust.ire.db.persistence.service.monitoring.PerformanceMonitor;
import org.hkust.ire.db.persistence.service.review.ManualReviewService;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Comprehensive waterfall-focused tests for Phase 1 identity resolution flows.
 *
 * <p>This suite validates tier cascade behavior, source credibility scoring impact,
 * edge inputs, confidence boundaries, and manual review routing outcomes.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Identity Resolution Service - Waterfall Coverage")
public class IdentityResolutionServiceWaterfallTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolutionServiceWaterfallTest.class);

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

    @Mock
    private ConfidenceCalculator confidenceCalculator;

    @Mock
    private SourceCredibilityScorer sourceCredibilityScorer;

    @Mock
    private PerformanceMonitor performanceMonitor;

    private WaterfallMatchingEngine waterfallMatchingEngine;
    private MatchingEngineService matchingEngineService;
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up IdentityResolutionService waterfall tests");

        waterfallMatchingEngine = new WaterfallMatchingEngine();
        ReflectionTestUtils.setField(waterfallMatchingEngine, "identityRepository", identityRepository);
        ReflectionTestUtils.setField(waterfallMatchingEngine, "confidenceCalculator", confidenceCalculator);
        ReflectionTestUtils.setField(waterfallMatchingEngine, "tier2CandidateLimit", 500);

        matchingEngineService = new MatchingEngineService();
        ReflectionTestUtils.setField(matchingEngineService, "waterfallMatchingEngine", waterfallMatchingEngine);
        ReflectionTestUtils.setField(matchingEngineService, "sourceCredibilityScorer", sourceCredibilityScorer);
        ReflectionTestUtils.setField(matchingEngineService, "performanceMonitor", performanceMonitor);

        identityResolutionService = new IdentityResolutionService();
        ReflectionTestUtils.setField(identityResolutionService, "matchingEngineService", matchingEngineService);
        ReflectionTestUtils.setField(identityResolutionService, "identityRepository", identityRepository);
        ReflectionTestUtils.setField(identityResolutionService, "identityLinkRepository", identityLinkRepository);
        ReflectionTestUtils.setField(identityResolutionService, "auditLogRepository", auditLogRepository);
        ReflectionTestUtils.setField(identityResolutionService, "manualReviewService", manualReviewService);
        ReflectionTestUtils.setField(identityResolutionService, "identityCacheService", identityCacheService);

        lenient().when(sourceCredibilityScorer.score(anyString())).thenReturn(1.0);
        lenient().when(identityRepository.save(any(IdentityDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(manualReviewService.createReview(anyString(), anyString(), anyDouble(), any()))
                .thenReturn(new ManualReviewDAO());
    }

    private CanonicalIdentity canonical(String sourceSystem) {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setSourceSystem(sourceSystem);
        canonical.setSourceId("SRC-001");
        return canonical;
    }

    private IdentityDAO identity(String goldenId, String email) {
        IdentityDAO dao = new IdentityDAO();
        dao.setGoldenId(goldenId);
        dao.setEmail(email);
        dao.setStatus("ACTIVE");
        return dao;
    }

    private void stubTier1Misses() {
        when(identityRepository.findByHkid(anyString())).thenReturn(Optional.empty());
        when(identityRepository.findByStaffId(anyString())).thenReturn(Optional.empty());
        when(identityRepository.findByStudentId(anyString())).thenReturn(Optional.empty());
        when(identityRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    /** Verifies cascade path where deterministic tier misses and Tier-2 high score matches. */
    @Test
    public void testWaterfallCascadeTier1FailTier2HighConfidence() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-T2-HIGH", "high@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.95);

        CanonicalIdentity canonical = canonical("CRM");
        canonical.setEmail("high@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertEquals(0.95, response.getConfidenceScore(), 0.0001);
        assertEquals("GID-T2-HIGH", response.getGoldenId());
    }

    /** Verifies cascade path where deterministic tier misses and Tier-2 mid score still matches. */
    @Test
    public void testWaterfallCascadeTier1FailTier2MidConfidence() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-T2-MID", "mid@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.75);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("mid@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        assertEquals(0.75, response.getConfidenceScore(), 0.0001);
    }

    /** Verifies full cascade miss routes to review queue when identity is not new. */
    @Test
    public void testWaterfallCascadeAllTiersFailRoutesToManualReview() {
        stubTier1Misses();
        IdentityDAO weakCandidate = identity("GID-WEAK", "candidate@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(weakCandidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(weakCandidate))).thenReturn(0.20);

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setFirstName("Weak");
        canonical.setLastName("Match");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());
        assertEquals("REVIEW_QUEUED", response.getStatus());
        verify(manualReviewService, times(1)).createReview(anyString(), eq("THIRD_PARTY"), eq(0.20), eq(null));
    }

    /** Verifies Tier-1 deterministic match short-circuits Tier-2 candidate scan. */
    @Test
    public void testWaterfallCascadeTier1ShortCircuitTier2Evaluation() {
        IdentityDAO existing = identity("GID-T1", "tier1@ust.hk");
        when(identityRepository.findByHkid("A123456(7)")).thenReturn(Optional.of(existing));

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setHkid("A123456(7)");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        verify(identityRepository, never()).findByStatus(anyString(), any());
    }

    /** Verifies identical base match score yields different adjusted score by source credibility. */
    @Test
    public void testSourceCredibilitySameBaseScoreDifferentFinalScore() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-CRED", "cred@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.80);
        when(sourceCredibilityScorer.score("ADMS")).thenReturn(0.9);
        when(sourceCredibilityScorer.score("THIRD_PARTY")).thenReturn(0.7);

        CanonicalIdentity adms = canonical("ADMS");
        adms.setEmail("cred@ust.hk");
        CanonicalIdentity thirdParty = canonical("THIRD_PARTY");
        thirdParty.setEmail("cred@ust.hk");

        IdentityMatchResponse admsResponse = identityResolutionService.resolve(adms);
        IdentityMatchResponse thirdPartyResponse = identityResolutionService.resolve(thirdParty);

        assertEquals(0.72, admsResponse.getConfidenceScore(), 0.0001);
        assertEquals(0.56, thirdPartyResponse.getConfidenceScore(), 0.0001);
        assertTrue(admsResponse.getConfidenceScore() > thirdPartyResponse.getConfidenceScore());
    }

    /** Verifies ADMS source multiplier (0.9x) is applied to base confidence. */
    @Test
    public void testSourceCredibilityMultiplierAdmsApplied() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-ADMS", "adms@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.90);
        when(sourceCredibilityScorer.score("ADMS")).thenReturn(0.9);

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setEmail("adms@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.81, response.getConfidenceScore(), 0.0001);
    }

    /** Verifies third-party source multiplier (0.7x) is applied to base confidence. */
    @Test
    public void testSourceCredibilityMultiplierThirdPartyApplied() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-3P", "third@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.90);
        when(sourceCredibilityScorer.score("THIRD_PARTY")).thenReturn(0.7);

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setEmail("third@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals(0.63, response.getConfidenceScore(), 0.0001);
    }

    /** Verifies low adjusted confidence can route to manual review when matching result is Tier-3. */
    @Test
    public void testSourceCredibilityBelowFiftyRoutesToManualReview() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-LOW-CRED", "candidate@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.40);
        when(sourceCredibilityScorer.score("THIRD_PARTY")).thenReturn(0.7);

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setFirstName("Low");
        canonical.setLastName("Credibility");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals("REVIEW_QUEUED", response.getStatus());
        assertTrue(response.getConfidenceScore() < 0.50);
    }

    /** Verifies null email does not block Tier-1 HKID deterministic matching. */
    @Test
    public void testEdgeCaseNullEmailWithValidHkid() {
        IdentityDAO existing = identity("GID-HKID", "hkid@ust.hk");
        when(identityRepository.findByHkid("D123456(8)")).thenReturn(Optional.of(existing));

        CanonicalIdentity canonical = canonical("ADMS");
        canonical.setHkid("D123456(8)");
        canonical.setEmail(null);

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_1, response.getMatchTier());
        assertEquals("GID-HKID", response.getGoldenId());
    }

    /** Verifies empty-string deterministic fields are treated as absent and fall through to Tier-2. */
    @Test
    public void testEdgeCaseEmptyStringsTreatedAsNull() {
        IdentityDAO candidate = identity("GID-EMPTY", "empty@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.74);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setHkid("");
        canonical.setStaffId("");
        canonical.setStudentId("");
        canonical.setEmail("");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
        verify(identityRepository, never()).findByEmail("");
    }

    /** Verifies special characters in incoming fields can still be scored in Tier-2 matching. */
    @Test
    public void testEdgeCaseSpecialCharactersInNameAndEmail() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-SPECIAL", "josé+event@example.com");
        candidate.setFirstName("José");
        candidate.setLastName("García");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.88);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("josé+event@example.com");
        canonical.setFirstName("José");
        canonical.setLastName("García");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.88, response.getConfidenceScore(), 0.0001);
    }

    /** Verifies whitespace-insensitive comparisons are supported in Tier-2 composite scoring scenarios. */
    @Test
    public void testEdgeCaseWhitespaceNormalizationInTier2() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-SPACE", "john@example.com");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.78);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("  john@example.com  ");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertTrue(response.getConfidenceScore() >= 0.70);
    }

    /** Verifies case-insensitive matching behavior in Tier-2 candidate evaluation path. */
    @Test
    public void testEdgeCaseCaseInsensitiveMatching() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-CASE", "john@example.com");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.76);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setEmail("JOHN@EXAMPLE.COM");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(MatchTierConstant.TIER_2, response.getMatchTier());
    }

    /** Verifies confidence at 95% remains eligible for automatic matched flow. */
    @Test
    public void testConfidenceBoundaryExactlyNinetyFivePercent() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-95", "exact95@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.95);

        CanonicalIdentity canonical = canonical("CRM");
        canonical.setEmail("exact95@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals(0.95, response.getConfidenceScore(), 0.0001);
    }

    /** Verifies confidence just below 95% can be routed to review when matching marks Tier-3. */
    @Test
    public void testConfidenceBoundaryJustBelowNinetyFivePercent() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-949", "candidate@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.69);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setFirstName("Below");
        canonical.setLastName("Threshold");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals(MatchTierConstant.TIER_3, response.getMatchTier());
        assertEquals("REVIEW_QUEUED", response.getStatus());
    }

    /** Verifies confidence below 50% supports new-record creation when source identity is new. */
    @Test
    public void testConfidenceBoundaryBelowFiftyCreatesNewRecord() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-LOW", "new-low@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.45);
        when(identityRepository.findByEmail("new-low@ust.hk")).thenReturn(Optional.empty());

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setEmail("new-low@ust.hk");
        canonical.setFirstName("New");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals("NEW_IDENTITY", response.getStatus());
        assertNotNull(response.getGoldenId());
    }

    /** Verifies zero confidence with no candidates still follows unmatched/new identity path safely. */
    @Test
    public void testConfidenceBoundaryZeroConfidenceNoMatch() {
        stubTier1Misses();
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(identityRepository.findByEmail("zero@ust.hk")).thenReturn(Optional.empty());

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setEmail("zero@ust.hk");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertTrue(response.isMatched());
        assertEquals("NEW_IDENTITY", response.getStatus());
        assertNotNull(response.getGoldenId());
    }

    /** Verifies manual review service invocation and source propagation for review queue routing. */
    @Test
    public void testManualReviewQueueProperRoutingToReviewService() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-REVIEW", "candidate@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.30);

        CanonicalIdentity canonical = canonical("THIRD_PARTY");
        canonical.setFirstName("Needs");
        canonical.setLastName("Review");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals("REVIEW_QUEUED", response.getStatus());
        verify(manualReviewService).createReview(anyString(), eq("THIRD_PARTY"), eq(0.30), eq(null));
    }

    /** Verifies review task payload captures conflicting fields for downstream manual analysis. */
    @Test
    public void testManualReviewQueueCreationWithConflictingFields() {
        stubTier1Misses();
        IdentityDAO candidateA = identity("GID-CONFLICT-A", "candidate-a@ust.hk");
        IdentityDAO candidateB = identity("GID-CONFLICT-B", "candidate-b@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Arrays.asList(candidateA, candidateB)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidateA))).thenReturn(0.45);
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidateB))).thenReturn(0.44);

        CanonicalIdentity canonical = canonical("EVENT_SYSTEM");
        canonical.setFirstName("Conflict");
        canonical.setLastName("Candidate");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertFalse(response.isMatched());
        assertEquals("REVIEW_QUEUED", response.getStatus());
        assertEquals(0.45, response.getConfidenceScore(), 0.0001);
        verify(manualReviewService).createReview(anyString(), eq("EVENT_SYSTEM"), eq(0.45), eq(null));
    }

    /** Verifies response status remains REVIEW_QUEUED after queueing manual review work item. */
    @Test
    public void testManualReviewQueueStatusTrackingReviewQueued() {
        stubTier1Misses();
        IdentityDAO candidate = identity("GID-STATUS", "candidate@ust.hk");
        when(identityRepository.findByStatus(eq("ACTIVE"), any())).thenReturn(new PageImpl<>(Collections.singletonList(candidate)));
        when(confidenceCalculator.calculate(any(CanonicalIdentity.class), eq(candidate))).thenReturn(0.35);

        CanonicalIdentity canonical = canonical("GOOGLE_FORMS");
        canonical.setFirstName("Queue");
        canonical.setLastName("Status");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertEquals("REVIEW_QUEUED", response.getStatus());
        verify(manualReviewService).createReview(anyString(), eq("GOOGLE_FORMS"), eq(0.35), eq(null));
    }
}
