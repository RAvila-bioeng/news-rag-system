package com.ragnews.ingestion.storage;

public record IndexingSummary(
        int indexedCount,
        int createdCount,
        int updatedCount
) {
}
