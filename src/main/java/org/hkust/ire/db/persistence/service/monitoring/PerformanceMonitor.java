package org.hkust.ire.db.persistence.service.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks latency and counts for key operations in the IRE system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class PerformanceMonitor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ConcurrentHashMap<String, AtomicLong> latencyTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> callCounts = new ConcurrentHashMap<>();

    /**
     * Records a latency measurement for the given operation.
     *
     * @param operation the operation name
     * @param latencyMs latency in milliseconds
     */
    public void recordLatency(String operation, long latencyMs) {
        try {
            latencyTotals.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(latencyMs);
            callCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
            log.debug("Recorded latency: operation={}, latencyMs={}", operation, latencyMs);
        } catch (Exception e) {
            log.error("Error recording latency: {}", e.getMessage());
        }
    }

    /**
     * Returns the average latency for a given operation.
     *
     * @param operation the operation name
     * @return average latency in milliseconds, or 0 if no data
     */
    public double getAverageLatency(String operation) {
        try {
            AtomicLong total = latencyTotals.get(operation);
            AtomicLong count = callCounts.get(operation);
            if (total == null || count == null || count.get() == 0) {
                return 0.0;
            }
            return (double) total.get() / count.get();
        } catch (Exception e) {
            log.error("Error getting average latency: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Returns total call count for an operation.
     *
     * @param operation the operation name
     * @return call count
     */
    public long getCallCount(String operation) {
        AtomicLong count = callCounts.get(operation);
        return count != null ? count.get() : 0L;
    }
}
