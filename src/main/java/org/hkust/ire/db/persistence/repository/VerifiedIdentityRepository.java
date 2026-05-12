package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.VerifiedIdentityDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for VerifiedIdentityDAO - IAM verified identities.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface VerifiedIdentityRepository extends CommonRepository<VerifiedIdentityDAO, Long> {

    @Transactional(readOnly = true)
    Optional<VerifiedIdentityDAO> findByGoldenId(String goldenId);

    @Transactional(readOnly = true)
    Optional<VerifiedIdentityDAO> findByIamId(String iamId);
}
