package org.hkust.ire.db.persistence.service.identity;

import org.hkust.ire.common.constant.MatchTierConstant;
import org.hkust.ire.common.constant.StatusConstant;
import org.hkust.ire.common.utils.GeneralUtil;
import org.hkust.ire.db.persistence.domain.AuditLogDAO;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.domain.IdentityLinkDAO;
import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.IdentityLinkRepository;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.db.persistence.service.review.ManualReviewService;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Main orchestration service for identity resolution.
 *
 * <p>Coordinates matching, identity creation/linking, review routing, and audit logging.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class IdentityResolutionService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MatchingEngineService matchingEngineService;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ManualReviewService manualReviewService;

    @Autowired
    private IdentityCacheService identityCacheService;

    /**
     * Resolves a canonical identity against existing records.
     *
     * <p>If TIER-1 or TIER-2 match found, links the source to the golden record.
     * If no match (TIER-3), creates a new golden record or routes to manual review.</p>
     *
     * @param canonical the normalized incoming identity
     * @return IdentityMatchResponse with resolution result
     */
    @Transactional
    public IdentityMatchResponse resolve(CanonicalIdentity canonical) {
        long start = System.currentTimeMillis();
        log.info("Resolving identity for sourceSystem={}", canonical.getSourceSystem());
        try {
            IdentityMatchResponse matchResponse = matchingEngineService.match(canonical);

            if (matchResponse.isMatched()) {
                linkSourceToGolden(canonical, matchResponse.getGoldenId(), matchResponse.getMatchTier());
                identityCacheService.evictIdentity(matchResponse.getGoldenId());
            } else if (MatchTierConstant.TIER_3.equals(matchResponse.getMatchTier())) {
                if (isNewIdentity(canonical)) {
                    IdentityDAO newIdentity = createNewGoldenRecord(canonical);
                    matchResponse.setGoldenId(newIdentity.getGoldenId());
                    matchResponse.setMatched(true);
                    matchResponse.setStatus("NEW_IDENTITY");
                    log.info("Created new golden record: {}", newIdentity.getGoldenId());
                } else {
                    routeToManualReview(canonical, matchResponse.getConfidenceScore());
                    matchResponse.setStatus("REVIEW_QUEUED");
                    log.info("Routed to manual review for sourceSystem={}", canonical.getSourceSystem());
                }
            }

            AuditLogDAO audit = new AuditLogDAO("IDENTITY_RESOLVED", "IDENTITY",
                    matchResponse.getGoldenId());
            audit.setSourceSystem(canonical.getSourceSystem());
            audit.setDetails("tier=" + matchResponse.getMatchTier()
                    + ", score=" + matchResponse.getConfidenceScore()
                    + ", elapsed=" + (System.currentTimeMillis() - start) + "ms");
            auditLogRepository.save(audit);

            return matchResponse;

        } catch (Exception e) {
            log.error("Error resolving identity: {}", e.getMessage());
            IdentityMatchResponse error = new IdentityMatchResponse();
            error.setMatched(false);
            error.setStatus("ERROR");
            return error;
        }
    }

    private void linkSourceToGolden(CanonicalIdentity canonical, String goldenId, String matchTier) {
        try {
            IdentityLinkDAO link = new IdentityLinkDAO(goldenId,
                    canonical.getSourceSystem(), canonical.getSourceId());
            link.setMatchTier(matchTier);
            link.setStatus(StatusConstant.ACTIVE);
            identityLinkRepository.save(link);
        } catch (Exception e) {
            log.error("Error linking source to golden: {}", e.getMessage());
        }
    }

    private boolean isNewIdentity(CanonicalIdentity canonical) {
        if (canonical.getEmail() == null || canonical.getEmail().isEmpty()) {
            return false;
        }
        Optional<IdentityDAO> existing = identityRepository.findByEmail(canonical.getEmail());
        return !existing.isPresent();
    }

    private IdentityDAO createNewGoldenRecord(CanonicalIdentity canonical) {
        String goldenId = "GID-" + GeneralUtil.generateUuid().substring(0, 8).toUpperCase();
        IdentityDAO identity = new IdentityDAO(goldenId,
                canonical.getEmail() != null ? canonical.getEmail() : "unknown@ire.hkust",
                StatusConstant.ACTIVE);
        identity.setHkid(canonical.getHkid());
        identity.setStaffId(canonical.getStaffId());
        identity.setStudentId(canonical.getStudentId());
        identity.setFirstName(canonical.getFirstName());
        identity.setLastName(canonical.getLastName());
        identity.setPhone(canonical.getPhone());
        identity.setPrimarySource(canonical.getSourceSystem());
        identity.setConfidenceScore(1.0);
        identity.setCreatedBy("IRE_SYSTEM");
        return identityRepository.save(identity);
    }

    private void routeToManualReview(CanonicalIdentity canonical, Double score) {
        try {
            String payload = "sourceSystem=" + canonical.getSourceSystem()
                    + ",email=" + canonical.getEmail()
                    + ",sourceId=" + canonical.getSourceId();
            manualReviewService.createReview(payload, canonical.getSourceSystem(), score, null);
        } catch (Exception e) {
            log.error("Error routing to manual review: {}", e.getMessage());
        }
    }
}
