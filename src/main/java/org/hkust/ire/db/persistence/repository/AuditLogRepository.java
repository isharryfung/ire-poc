package org.hkust.ire.db.persistence.repository;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.persistence.domain.AuditLogDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for AuditLogDAO - audit trail management.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Repository
public interface AuditLogRepository extends CommonRepository<AuditLogDAO, Long> {

    @Transactional(readOnly = true)
    List<AuditLogDAO> findByEntityId(String entityId);

    @Transactional(readOnly = true)
    List<AuditLogDAO> findByAction(String action);

    @Transactional(readOnly = true)
    List<AuditLogDAO> findBySourceSystem(String sourceSystem);
}
