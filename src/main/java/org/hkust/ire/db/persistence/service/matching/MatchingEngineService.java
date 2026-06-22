package org.hkust.ire.db.persistence.service.matching;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.service.monitoring.PerformanceMonitor;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Core matching engine service that orchestrates the waterfall matching algorithm
 * and applies source credibility weighting.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class MatchingEngineService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WaterfallMatchingEngine waterfallMatchingEngine;

    @Autowired
    private SourceCredibilityScorer sourceCredibilityScorer;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    /**
     * Executes identity matching with source credibility weighting applied to the score.
     *
     * @param canonical the normalized incoming identity
     * @return IdentityMatchResponse with credibility-adjusted score
     */
    public IdentityMatchResponse match(CanonicalIdentity canonical) {
        long startTime = System.currentTimeMillis();
        log.info("Starting match for sourceSystem={}", canonical.getSourceSystem());
        try {
            IdentityMatchResponse response = waterfallMatchingEngine.match(canonical);

            if (response.getConfidenceScore() != null && response.getConfidenceScore() > 0) {
                double originalScore = response.getConfidenceScore();
                double credibility = sourceCredibilityScorer.score(canonical.getSourceSystem());
                double adjustedScore = originalScore * credibility;
                response.setConfidenceScore(adjustedScore);
                log.debug("Applied credibility={} to score: {} -> {}",
                        credibility, originalScore, adjustedScore);

                if (MatchTierConstant.TIER_2.equals(response.getMatchTier())
                        && response.isMatched()
                        && adjustedScore < MatchTierConstant.TIER_2_THRESHOLD) {
                    response.setMatched(false);
                    response.setStatus("REVIEW_REQUIRED");
                    response.setMatchTier(MatchTierConstant.TIER_3);
                    response.setGoldenId(null);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            performanceMonitor.recordLatency("matching", elapsed);
            log.info("Match completed in {}ms: tier={}, matched={}",
                    elapsed, response.getMatchTier(), response.isMatched());
            return response;

        } catch (Exception e) {
            log.error("Error in matching engine: {}", e.getMessage());
            IdentityMatchResponse error = new IdentityMatchResponse();
            error.setMatched(false);
            error.setStatus("ERROR");
            return error;
        }
    }
}
