package com.university.ire.aspect;

import com.university.ire.service.monitoring.MetricsService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceAspect {

    private final MetricsService metricsService;

    public PerformanceAspect(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Around("execution(* com.university.ire.service..*(..))")
    public Object profile(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            metricsService.recordLatency(joinPoint.getSignature().toShortString(), System.nanoTime() - start);
        }
    }
}
