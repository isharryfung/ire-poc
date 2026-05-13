package org.hkust.ire.db.persistence.service.monitoring;

import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.repository.ManualReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects and exposes custom metrics for the IRE system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class MetricsService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    /**
     * Collects a snapshot of current system metrics.
     *
     * @return map of metric names to values
     */
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        try {
            metrics.put("totalIdentities", identityRepository.count());
            metrics.put("activeIdentities", identityRepository.findByStatus("ACTIVE").size());
            metrics.put("pendingReviews", manualReviewRepository.countByStatus("PENDING"));
            metrics.put("avgMatchingLatencyMs", performanceMonitor.getAverageLatency("matching"));
            metrics.put("totalMatchingCalls", performanceMonitor.getCallCount("matching"));
            log.debug("Collected metrics: {}", metrics);
        } catch (Exception e) {
            log.error("Error collecting metrics: {}", e.getMessage());
            metrics.put("error", e.getMessage());
        }
        return metrics;
    }
}
