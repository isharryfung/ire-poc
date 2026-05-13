package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.IdentityLinkDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for IdentityLinkDAO - source system links.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface IdentityLinkRepository extends CommonRepository<IdentityLinkDAO, Long> {

    @Transactional(readOnly = true)
    List<IdentityLinkDAO> findByGoldenId(String goldenId);

    @Transactional(readOnly = true)
    List<IdentityLinkDAO> findBySourceSystem(String sourceSystem);

    @Transactional(readOnly = true)
    IdentityLinkDAO findBySourceSystemAndSourceId(String sourceSystem, String sourceId);
}
