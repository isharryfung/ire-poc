package com.university.ire.service.monitoring;

import org.springframework.stereotype.Component;

@Component
public class PerformanceMonitor {

    private final MetricsService metricsService;

    public PerformanceMonitor(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void track(String operation, Runnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            metricsService.recordLatency(operation, System.nanoTime() - start);
        }
    }
}
