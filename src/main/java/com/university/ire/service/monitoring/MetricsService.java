package com.university.ire.service.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementTier(String tier) {
        meterRegistry.counter("ire.match.tier", "tier", tier).increment();
    }

    public void recordConfidence(double confidence) {
        meterRegistry.summary("ire.match.confidence").record(confidence);
    }

    public void recordLatency(String operation, long nanos) {
        Timer.builder("ire.operation.latency")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    public void setReviewQueueDepth(int depth) {
        meterRegistry.gauge("ire.review.queue.depth", depth);
    }
}
