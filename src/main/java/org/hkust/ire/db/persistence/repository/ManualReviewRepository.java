package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ManualReviewDAO - review queue management.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface ManualReviewRepository extends CommonRepository<ManualReviewDAO, Long> {

    @Transactional(readOnly = true)
    Optional<ManualReviewDAO> findByReviewId(String reviewId);

    @Transactional(readOnly = true)
    List<ManualReviewDAO> findByStatus(String status);

    @Transactional(readOnly = true)
    List<ManualReviewDAO> findBySourceSystem(String sourceSystem);

    @Transactional(readOnly = true)
    long countByStatus(String status);
}
