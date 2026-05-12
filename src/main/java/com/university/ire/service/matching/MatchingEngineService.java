package com.university.ire.service.matching;

import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.entity.Identity;
import org.springframework.stereotype.Service;

@Service
public class MatchingEngineService {

    public record MatchingOutcome(
            WaterfallMatchingEngine.Tier tier,
            String action,
            double confidence,
            Identity matchedIdentity
    ) {}

    private final WaterfallMatchingEngine waterfallMatchingEngine;
    private final SourceCredibilityScorer sourceCredibilityScorer;
    private final ConfidenceCalculator confidenceCalculator;

    public MatchingEngineService(
            WaterfallMatchingEngine waterfallMatchingEngine,
            SourceCredibilityScorer sourceCredibilityScorer,
            ConfidenceCalculator confidenceCalculator) {
        this.waterfallMatchingEngine = waterfallMatchingEngine;
        this.sourceCredibilityScorer = sourceCredibilityScorer;
        this.confidenceCalculator = confidenceCalculator;
    }

    public MatchingOutcome resolve(CanonicalIdentity canonicalIdentity) {
        WaterfallMatchingEngine.MatchResult match = waterfallMatchingEngine.match(canonicalIdentity);
        double sourceScore = sourceCredibilityScorer.scoreFor(canonicalIdentity.getSourceSystem());
        double confidence = confidenceCalculator.composite(match.baseScore(), sourceScore);
        String action;
        if (confidence >= 0.95 && match.identity().isPresent()) {
            action = "AUTO_MERGE";
        } else if (confidence >= 0.50) {
            action = "MANUAL_REVIEW";
        } else {
            action = "CREATE_NEW";
        }
        return new MatchingOutcome(match.tier(), action, confidence, match.identity().orElse(null));
    }
}
