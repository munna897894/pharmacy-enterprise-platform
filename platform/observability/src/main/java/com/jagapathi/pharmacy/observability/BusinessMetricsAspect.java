package com.jagapathi.pharmacy.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public final class BusinessMetricsAspect {
    private final PharmacyBusinessMetrics metrics;

    public BusinessMetricsAspect(PharmacyBusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("@annotation(recordBusinessMetric)")
    public Object recordOutcome(ProceedingJoinPoint invocation,
                                RecordBusinessMetric recordBusinessMetric) throws Throwable {
        try {
            Object result = invocation.proceed();
            metrics.record(recordBusinessMetric.value(), BusinessOutcome.SUCCESS);
            return result;
        } catch (Throwable failure) {
            metrics.record(recordBusinessMetric.value(), BusinessOutcome.FAILURE);
            throw failure;
        }
    }
}
