package org.hkust.ire.db.persistence.service.identity;

import org.hkust.ire.common.constant.StatusConstant;
import org.hkust.ire.db.persistence.domain.AuditLogDAO;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Handles merging of duplicate identity records.
 *
 * <p>Merges a source identity into a target golden record,
 * updating the source to MERGED status.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class IdentityMergeService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private IdentityCacheService identityCacheService;

    /**
     * Merges the source identity into the target golden record.
     *
     * @param sourceGoldenId the identity to merge (will be deactivated)
     * @param targetGoldenId the winning golden record
     * @param mergedBy       the user/system performing the merge
     * @return updated target IdentityDAO
     */
    @Transactional
    public IdentityDAO merge(String sourceGoldenId, String targetGoldenId, String mergedBy) {
        log.info("Merging identity {} into {}", sourceGoldenId, targetGoldenId);
        try {
            Optional<IdentityDAO> sourceOpt = identityRepository.findByGoldenId(sourceGoldenId);
            Optional<IdentityDAO> targetOpt = identityRepository.findByGoldenId(targetGoldenId);

            if (!sourceOpt.isPresent() || !targetOpt.isPresent()) {
                throw new RuntimeException("Identity not found for merge: source=" + sourceGoldenId
                        + ", target=" + targetGoldenId);
            }

            IdentityDAO source = sourceOpt.get();
            IdentityDAO target = targetOpt.get();

            // Fill missing fields in target from source
            if (target.getHkid() == null && source.getHkid() != null) {
                target.setHkid(source.getHkid());
            }
            if (target.getPhone() == null && source.getPhone() != null) {
                target.setPhone(source.getPhone());
            }

            target.setUpdatedBy(mergedBy);
            target.setUpdatedDate(new Date());
            identityRepository.save(target);

            // Mark source as merged
            source.setStatus(StatusConstant.MERGED);
            source.setUpdatedBy(mergedBy);
            source.setUpdatedDate(new Date());
            identityRepository.save(source);

            // Audit
            AuditLogDAO audit = new AuditLogDAO("IDENTITY_MERGED", "IDENTITY", sourceGoldenId);
            audit.setDetails("Merged into " + targetGoldenId);
            audit.setPerformedBy(mergedBy);
            auditLogRepository.save(audit);

            // Evict from cache
            identityCacheService.evictIdentity(sourceGoldenId);
            identityCacheService.evictIdentity(targetGoldenId);

            log.info("Merge completed: {} -> {}", sourceGoldenId, targetGoldenId);
            return target;

        } catch (Exception e) {
            log.error("Error merging identities: {}", e.getMessage());
            throw e;
        }
    }
}
