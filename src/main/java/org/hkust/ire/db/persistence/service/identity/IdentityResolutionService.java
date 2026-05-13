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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Main orchestration service for identity resolution.
 *
 * <p>Coordinates matching, identity creation/linking, review routing, and audit logging.</p>
 *
 * <p><strong>Transaction Strategy:</strong> Uses {@link Propagation#REQUIRES_NEW} to isolate
 * each resolution attempt in its own transaction. This prevents rollback-only marking if
 * exceptions occur in underlying services.</p>
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
     * <p><strong>Transaction Isolation:</strong> Each call uses {@link Propagation#REQUIRES_NEW}
     * to create an independent transaction. This prevents cascading rollback-only errors
     * when exceptions occur in matching or cache operations.</p>
     *
     * @param canonical the normalized incoming identity
     * @return IdentityMatchResponse with resolution result
     * @throws RuntimeException if critical database operation fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdentityMatchResponse resolve(CanonicalIdentity canonical) {
        long start = System.currentTimeMillis();
        log.info("Resolving identity for sourceSystem={}", canonical.getSourceSystem());

        IdentityMatchResponse matchResponse = matchingEngineService.match(canonical);

        if (matchResponse.isMatched()) {
            linkSourceToGolden(canonical, matchResponse.getGoldenId(), matchResponse.getMatchTier());
            
            // Cache eviction failures should not fail the entire request
            try {
                identityCacheService.evictIdentity(matchResponse.getGoldenId());
            } catch (Exception e) {
                log.warn("Cache eviction failed for goldenId={}: {}", matchResponse.getGoldenId(), e.getMessage());
            }
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

        // Audit logging - record the resolution attempt
        AuditLogDAO audit = new AuditLogDAO("IDENTITY_RESOLVED", "IDENTITY",
                matchResponse.getGoldenId());
        audit.setSourceSystem(canonical.getSourceSystem());
        audit.setDetails("tier=" + matchResponse.getMatchTier()
                + ", score=" + matchResponse.getConfidenceScore()
                + ", elapsed=" + (System.currentTimeMillis() - start) + "ms");
        auditLogRepository.save(audit);

        log.debug("Identity resolution completed for sourceSystem={}, tier={}, score={}",
                canonical.getSourceSystem(), matchResponse.getMatchTier(), matchResponse.getConfidenceScore());

        return matchResponse;
    }

    /**
     * Links a source record to a golden identity record.
     *
     * @param canonical the canonical identity
     * @param goldenId  the target golden ID
     * @param matchTier the matching tier achieved
     */
    @Transactional
    private void linkSourceToGolden(CanonicalIdentity canonical, String goldenId, String matchTier) {
        log.debug("Linking source {} to golden {}", canonical.getSourceId(), goldenId);
        IdentityLinkDAO link = new IdentityLinkDAO(goldenId,
                canonical.getSourceSystem(), canonical.getSourceId());
        link.setMatchTier(matchTier);
        link.setStatus(StatusConstant.ACTIVE);
        identityLinkRepository.save(link);
    }

    /**
     * Checks if the identity is new (not already in database).
     *
     * @param canonical the canonical identity to check
     * @return true if new identity, false otherwise
     */
    private boolean isNewIdentity(CanonicalIdentity canonical) {
        if (canonical.getEmail() == null || canonical.getEmail().isEmpty()) {
            return false;
        }
        Optional<IdentityDAO> existing = identityRepository.findByEmail(canonical.getEmail());
        return !existing.isPresent();
    }

    /**
     * Creates a new golden identity record.
     *
     * @param canonical the canonical identity to save
     * @return the newly created IdentityDAO
     */
    @Transactional
    private IdentityDAO createNewGoldenRecord(CanonicalIdentity canonical) {
        log.debug("Creating new golden record for email={}", canonical.getEmail());
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

    /**
     * Routes an identity match to manual review queue.
     *
     * @param canonical the canonical identity
     * @param score     the confidence score
     */
    private void routeToManualReview(CanonicalIdentity canonical, Double score) {
        log.debug("Routing to manual review: sourceSystem={}, email={}, score={}",
                canonical.getSourceSystem(), canonical.getEmail(), score);
        try {
            String payload = "sourceSystem=" + canonical.getSourceSystem()
                    + ",email=" + canonical.getEmail()
                    + ",sourceId=" + canonical.getSourceId();
            manualReviewService.createReview(payload, canonical.getSourceSystem(), score, null);
        } catch (Exception e) {
            log.error("Error routing to manual review: {}", e.getMessage(), e);
            // Don't rethrow - manual review failure should not fail the request
        }
    }
}
