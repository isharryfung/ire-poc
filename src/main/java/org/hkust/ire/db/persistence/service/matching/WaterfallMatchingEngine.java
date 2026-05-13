package org.hkust.ire.db.persistence.service.matching;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Implements the waterfall matching algorithm with three tiers.
 *
 * <ul>
 *   <li>TIER-1: Deterministic exact-match on unique IDs (HKID, staffId, studentId, email)</li>
 *   <li>TIER-2: Probabilistic composite scoring against paginated candidate set</li>
 *   <li>TIER-3: Insufficient evidence - routes to manual review queue</li>
 * </ul>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class WaterfallMatchingEngine {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private ConfidenceCalculator confidenceCalculator;

    /** Maximum candidate records to evaluate in Tier-2 (prevents full table load) */
    @Value("${ire.matching.tier2.candidate-limit:500}")
    private int tier2CandidateLimit;

    /**
     * Executes the waterfall matching algorithm.
     *
     * @param canonical the normalized incoming identity
     * @return IdentityMatchResponse with tier, score, and matched goldenId
     */
    public IdentityMatchResponse match(CanonicalIdentity canonical) {
        log.info("Starting waterfall match for sourceSystem={}", canonical.getSourceSystem());
        try {
            IdentityMatchResponse tier1 = attemptTier1Match(canonical);
            if (tier1 != null && tier1.isMatched()) {
                log.info("TIER-1 match: goldenId={}", tier1.getGoldenId());
                return tier1;
            }

            IdentityMatchResponse tier2 = attemptTier2Match(canonical);
            if (tier2 != null && tier2.isMatched()) {
                log.info("TIER-2 match: goldenId={}, score={}", tier2.getGoldenId(), tier2.getConfidenceScore());
                return tier2;
            }

            IdentityMatchResponse noMatch = new IdentityMatchResponse();
            noMatch.setMatched(false);
            noMatch.setMatchTier(MatchTierConstant.TIER_3);
            noMatch.setStatus("REVIEW_REQUIRED");
            noMatch.setConfidenceScore(tier2 != null ? tier2.getConfidenceScore() : 0.0);
            log.info("No match found - routing to manual review");
            return noMatch;

        } catch (Exception e) {
            log.error("Error in waterfall matching: {}", e.getMessage());
            IdentityMatchResponse error = new IdentityMatchResponse();
            error.setMatched(false);
            error.setStatus("ERROR");
            return error;
        }
    }

    /**
     * Tier 1: deterministic exact match on unique identifiers.
     */
    private IdentityMatchResponse attemptTier1Match(CanonicalIdentity canonical) {
        try {
            Optional<IdentityDAO> found = Optional.empty();

            if (canonical.getHkid() != null && !canonical.getHkid().isEmpty()) {
                found = identityRepository.findByHkid(canonical.getHkid());
            }
            if (!found.isPresent() && canonical.getStaffId() != null && !canonical.getStaffId().isEmpty()) {
                found = identityRepository.findByStaffId(canonical.getStaffId());
            }
            if (!found.isPresent() && canonical.getStudentId() != null && !canonical.getStudentId().isEmpty()) {
                found = identityRepository.findByStudentId(canonical.getStudentId());
            }
            if (!found.isPresent() && canonical.getEmail() != null && !canonical.getEmail().isEmpty()) {
                found = identityRepository.findByEmail(canonical.getEmail());
            }

            if (found.isPresent()) {
                IdentityDAO identity = found.get();
                IdentityMatchResponse response = new IdentityMatchResponse();
                response.setGoldenId(identity.getGoldenId());
                response.setMatchTier(MatchTierConstant.TIER_1);
                response.setConfidenceScore(1.0);
                response.setMatched(true);
                response.setStatus("MATCHED");
                return response;
            }
            return null;
        } catch (Exception e) {
            log.error("Error in Tier-1 matching: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tier 2: probabilistic scoring against paginated active identities.
     * Uses configurable candidate limit to avoid loading the full dataset.
     */
    private IdentityMatchResponse attemptTier2Match(CanonicalIdentity canonical) {
        try {
            Page<IdentityDAO> candidatePage = identityRepository.findByStatus(
                    "ACTIVE", PageRequest.of(0, tier2CandidateLimit));
            List<IdentityDAO> candidates = candidatePage.getContent();

            IdentityDAO bestMatch = null;
            double bestScore = 0.0;

            for (IdentityDAO candidate : candidates) {
                double score = confidenceCalculator.calculate(canonical, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = candidate;
                }
            }

            IdentityMatchResponse response = new IdentityMatchResponse();
            response.setConfidenceScore(bestScore);
            response.setMatchTier(MatchTierConstant.TIER_2);

            if (bestMatch != null && bestScore >= MatchTierConstant.TIER_2_THRESHOLD) {
                response.setGoldenId(bestMatch.getGoldenId());
                response.setMatched(true);
                response.setStatus("MATCHED");
            } else {
                response.setMatched(false);
                response.setStatus("NO_MATCH");
            }
            return response;
        } catch (Exception e) {
            log.error("Error in Tier-2 matching: {}", e.getMessage());
            return null;
        }
    }
}
