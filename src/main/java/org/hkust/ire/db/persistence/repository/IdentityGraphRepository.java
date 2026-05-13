package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.IdentityGraphDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for IdentityGraphDAO - identity graph relationships.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface IdentityGraphRepository extends CommonRepository<IdentityGraphDAO, Long> {

    @Transactional(readOnly = true)
    List<IdentityGraphDAO> findBySourceGoldenId(String sourceGoldenId);

    @Transactional(readOnly = true)
    List<IdentityGraphDAO> findBySourceGoldenIdOrTargetGoldenId(String sourceGoldenId, String targetGoldenId);
}
