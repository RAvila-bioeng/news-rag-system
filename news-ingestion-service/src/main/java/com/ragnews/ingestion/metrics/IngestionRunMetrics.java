package com.ragnews.ingestion.metrics;

import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.sentiment.Sentiment;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

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
        int embeddedCount,
        int positiveCount,
        int negativeCount,
        int neutralCount
) {
    public static IngestionRunMetrics successfulRun(
            Instant runAt,
            String source,
            String upstreamStatus,
            int totalResults,
            int fetchedCount,
            int normalizedCount,
            int discardedCount,
            List<ProcessedArticle> processedArticles
    ) {
        return new IngestionRunMetrics(
                runAt,
                source,
                "SUCCESS",
                upstreamStatus,
                totalResults,
                fetchedCount,
                normalizedCount,
                discardedCount,
                0,
                countEmbedded(processedArticles),
                countSentiment(processedArticles, Sentiment.POSITIVE),
                countSentiment(processedArticles, Sentiment.NEGATIVE),
                countSentiment(processedArticles, Sentiment.NEUTRAL)
        );
    }

    private static int countSentiment(List<ProcessedArticle> articles, Sentiment sentiment) {
        if (articles == null) {
            return 0;
        }

        return (int) articles.stream()
                .filter(article -> article.sentiment() == sentiment)
                .count();
    }

    private static int countEmbedded(List<ProcessedArticle> articles) {
        if (articles == null) {
            return 0;
        }

        return (int) articles.stream()
                .filter(article -> article.embedding() != null && !article.embedding().isEmpty())
                .count();
    }
}
