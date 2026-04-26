package com.ragnews.ingestion.model;

import com.ragnews.ingestion.parser.NormalizedArticle;
import com.ragnews.ingestion.sentiment.Sentiment;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProcessedArticle(
        NormalizedArticle article,
        Sentiment sentiment
) {
}
