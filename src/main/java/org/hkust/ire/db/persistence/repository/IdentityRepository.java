package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for IdentityDAO - golden identity records.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface IdentityRepository extends CommonRepository<IdentityDAO, Long> {

    @Transactional(readOnly = true)
    Optional<IdentityDAO> findByGoldenId(String goldenId);

    @Transactional(readOnly = true)
    Optional<IdentityDAO> findByEmail(String email);

    @Transactional(readOnly = true)
    Optional<IdentityDAO> findByHkid(String hkid);

    @Transactional(readOnly = true)
    Optional<IdentityDAO> findByStaffId(String staffId);

    @Transactional(readOnly = true)
    Optional<IdentityDAO> findByStudentId(String studentId);

    /**
     * Paginated retrieval of identities by status (used for Tier-2 matching to avoid full table scan).
     *
     * @param status   the identity status
     * @param pageable pagination parameters
     * @return page of matching identities
     */
    @Transactional(readOnly = true)
    Page<IdentityDAO> findByStatus(String status, Pageable pageable);

    @Transactional(readOnly = true)
    List<IdentityDAO> findByStatus(String status);

    @Transactional(readOnly = true)
    @Query("SELECT i FROM IdentityDAO i WHERE i.confidenceScore >= :threshold")
    List<IdentityDAO> findByConfidenceScoreGreaterThanEqual(@Param("threshold") Double threshold);
}
