package com.ragnews.ingestion.metrics;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record IngestionRunMetrics(
        Instant runAt,
        String source,
        String status,
        String upstreamStatus,
        int totalResults,
        int fetchedCount,
        int normalizedCount,
        int discardedCount,
        int failedRequests,
        int positiveCount,
        int negativeCount,
        int neutralCount
) {
}
