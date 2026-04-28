package com.ragnews.search.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SearchResult(
        String title,
        String source,
        String url,
        String sentiment,
        String timestamp,
        double score
) {
}
