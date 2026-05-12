package org.hkust.ire.db.persistence.service.iam;

import org.hkust.ire.db.persistence.domain.VerifiedIdentityDAO;
import org.hkust.ire.db.persistence.repository.VerifiedIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for looking up verified identities from IAM (Midpoint).
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class VerifiedIdentityService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private VerifiedIdentityRepository verifiedIdentityRepository;

    /**
     * Finds a verified identity by golden ID.
     *
     * @param goldenId the golden identity ID
     * @return optional VerifiedIdentityDAO
     */
    public Optional<VerifiedIdentityDAO> findByGoldenId(String goldenId) {
        log.debug("Looking up verified identity for goldenId={}", goldenId);
        try {
            return verifiedIdentityRepository.findByGoldenId(goldenId);
        } catch (Exception e) {
            log.error("Error finding verified identity for goldenId={}: {}", goldenId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates or updates a verified identity record.
     *
     * @param goldenId    the golden identity ID
     * @param iamId       the IAM system ID
     * @param itscAccount ITSC account
     * @param roles       comma-separated roles
     * @return saved VerifiedIdentityDAO
     */
    public VerifiedIdentityDAO upsertVerifiedIdentity(String goldenId, String iamId,
                                                       String itscAccount, String roles) {
        log.info("Upserting verified identity for goldenId={}", goldenId);
        try {
            VerifiedIdentityDAO dao = verifiedIdentityRepository.findByGoldenId(goldenId)
                    .orElse(new VerifiedIdentityDAO(goldenId, iamId));
            dao.setIamId(iamId);
            dao.setItscAccount(itscAccount);
            dao.setRoles(roles);
            dao.setVerifiedStatus("VERIFIED");
            return verifiedIdentityRepository.save(dao);
        } catch (Exception e) {
            log.error("Error upserting verified identity for goldenId={}: {}", goldenId, e.getMessage());
            throw e;
        }
    }
}
