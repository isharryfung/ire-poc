package org.hkust.ire.db.persistence.service.batch;

import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.repository.ManualReviewRepository;
import org.hkust.ire.db.persistence.service.identity.IdentityCacheService;
import org.hkust.ire.db.persistence.service.monitoring.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Batch processing service for periodic maintenance tasks.
 *
 * <p>Executed by the Quartz {@code IreProcessBatch} job every 30 minutes.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class BatchJobService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private IdentityCacheService identityCacheService;

    @Autowired
    private MetricsService metricsService;

    /**
     * Runs all batch maintenance tasks in sequence.
     */
    public void runBatchTasks() {
        log.info("Starting batch maintenance tasks");
        long start = System.currentTimeMillis();
        try {
            runReconciliation();
            archiveOldAuditLogs();
            logMetricsSnapshot();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Batch tasks completed in {}ms", elapsed);
        } catch (Exception e) {
            log.error("Error in batch tasks: {}", e.getMessage());
        }
    }

    /**
     * Reconciles identity counts and reports anomalies.
     */
    public void runReconciliation() {
        log.info("Running identity reconciliation");
        try {
            long total = identityRepository.count();
            long pending = manualReviewRepository.countByStatus("PENDING");
            log.info("Reconciliation: totalIdentities={}, pendingReviews={}", total, pending);
        } catch (Exception e) {
            log.error("Error in reconciliation: {}", e.getMessage());
        }
    }

    /**
     * Archives audit log entries older than 90 days.
     * (Stub - full Oracle-specific archival to be implemented in production)
     */
    public void archiveOldAuditLogs() {
        log.info("Archiving old audit logs (stub)");
    }

    /**
     * Logs a metrics snapshot to the application log.
     */
    public void logMetricsSnapshot() {
        try {
            log.info("Metrics snapshot: {}", metricsService.collectMetrics());
        } catch (Exception e) {
            log.error("Error logging metrics snapshot: {}", e.getMessage());
        }
    }
}
