package com.ragnews.ingestion.metrics;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

@Serdeable
public record MetricsFile(
        Instant lastRunAt,
        List<IngestionRunMetrics> runs
) {
}
