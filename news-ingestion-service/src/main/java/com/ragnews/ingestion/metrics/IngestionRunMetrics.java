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
        int indexedCount,
        int createdCount,
        int updatedCount,
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
            int indexedCount,
            int createdCount,
            int updatedCount,
            List<ProcessedArticle> processedArticles
    ) {
        int processedCount = processedArticles == null ? 0 : processedArticles.size();

        return new IngestionRunMetrics(
                runAt,
                source,
                "SUCCESS",
                upstreamStatus,
                totalResults,
                fetchedCount,
                normalizedCount,
                discardedCount,
                Math.max(0, processedCount - indexedCount),
                countEmbedded(processedArticles),
                indexedCount,
                createdCount,
                updatedCount,
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
