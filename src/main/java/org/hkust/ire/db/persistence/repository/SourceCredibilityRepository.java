package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.SourceCredibilityDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for SourceCredibilityDAO - source credibility weights.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface SourceCredibilityRepository extends CommonRepository<SourceCredibilityDAO, Long> {

    @Transactional(readOnly = true)
    Optional<SourceCredibilityDAO> findBySourceSystem(String sourceSystem);
}
