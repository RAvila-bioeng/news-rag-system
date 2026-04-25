package com.ragnews.ingestion.parser;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record NormalizedArticle(
        String externalId,
        String title,
        String content,
        String source,
        String author,
        String url,
        Instant publishedAt
) {
}
