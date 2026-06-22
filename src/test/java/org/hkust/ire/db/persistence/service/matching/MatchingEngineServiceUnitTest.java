package org.hkust.ire.db.persistence.service.matching;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.service.monitoring.PerformanceMonitor;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MatchingEngineServiceUnitTest {

    @Mock
    private WaterfallMatchingEngine waterfallMatchingEngine;

    @Mock
    private SourceCredibilityScorer sourceCredibilityScorer;

    @Mock
    private PerformanceMonitor performanceMonitor;

    @InjectMocks
    private MatchingEngineService matchingEngineService;

    @Test
    public void testTier2MatchDowngradedWhenAdjustedScoreDropsBelowThreshold() {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setSourceSystem("THIRD_PARTY");

        IdentityMatchResponse response = new IdentityMatchResponse();
        response.setMatched(true);
        response.setMatchTier(MatchTierConstant.TIER_2);
        response.setStatus("MATCHED");
        response.setGoldenId("GID-123");
        response.setConfidenceScore(0.72);

        when(waterfallMatchingEngine.match(canonical)).thenReturn(response);
        when(sourceCredibilityScorer.score("THIRD_PARTY")).thenReturn(0.9);

        IdentityMatchResponse result = matchingEngineService.match(canonical);

        assertNotNull(result);
        assertFalse(result.isMatched());
        assertEquals(MatchTierConstant.TIER_3, result.getMatchTier());
        assertEquals("REVIEW_REQUIRED", result.getStatus());
        assertNull(result.getGoldenId());
        assertEquals(0.648, result.getConfidenceScore(), 0.000001);
        verify(performanceMonitor).recordLatency(eq("matching"), anyLong());
    }

    @Test
    public void testTier1MatchRemainsMatchedAfterCredibilityAdjustment() {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setSourceSystem("ADMS");

        IdentityMatchResponse response = new IdentityMatchResponse();
        response.setMatched(true);
        response.setMatchTier(MatchTierConstant.TIER_1);
        response.setStatus("MATCHED");
        response.setGoldenId("GID-999");
        response.setConfidenceScore(1.0);

        when(waterfallMatchingEngine.match(canonical)).thenReturn(response);
        when(sourceCredibilityScorer.score("ADMS")).thenReturn(0.9);

        IdentityMatchResponse result = matchingEngineService.match(canonical);

        assertNotNull(result);
        assertTrue(result.isMatched());
        assertEquals(MatchTierConstant.TIER_1, result.getMatchTier());
        assertEquals("GID-999", result.getGoldenId());
        assertEquals(0.9, result.getConfidenceScore(), 0.000001);
        verify(performanceMonitor).recordLatency(eq("matching"), anyLong());
    }
}

