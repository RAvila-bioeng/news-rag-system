package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.enrichment.ArticleEnricher;
import com.ragnews.ingestion.fetcher.GenericHttpNewsFetcher;
import com.ragnews.ingestion.metrics.IngestionRunMetrics;
import com.ragnews.ingestion.metrics.MetricsWriter;
import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.GenericJsonArticleParser;
import com.ragnews.ingestion.parser.NormalizedArticle;
import com.ragnews.ingestion.storage.ArticleIndexer;
import com.ragnews.ingestion.storage.IndexingSummary;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);
    private static final String GENERIC_JSON_TYPE = "generic-json";

    private final SourceConfigurationLoader sourceConfigurationLoader;
    private final GenericHttpNewsFetcher genericHttpNewsFetcher;
    private final GenericJsonArticleParser genericJsonArticleParser;
    private final ArticleEnricher articleEnricher;
    private final ArticleIndexer articleIndexer;
    private final MetricsWriter metricsWriter;

    public IngestionService(
            SourceConfigurationLoader sourceConfigurationLoader,
            GenericHttpNewsFetcher genericHttpNewsFetcher,
            GenericJsonArticleParser genericJsonArticleParser,
            ArticleEnricher articleEnricher,
            ArticleIndexer articleIndexer,
            MetricsWriter metricsWriter
    ) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
        this.genericHttpNewsFetcher = genericHttpNewsFetcher;
        this.genericJsonArticleParser = genericJsonArticleParser;
        this.articleEnricher = articleEnricher;
        this.articleIndexer = articleIndexer;
        this.metricsWriter = metricsWriter;
    }

    public Map<String, Object> runIngestion() {
        LOG.info("Ingestion run started");

        List<SourceConfig> sources = sourceConfigurationLoader.loadSources().stream()
                .filter(this::shouldIngestSource)
                .toList();

        if (sources.isEmpty()) {
            throw new IllegalStateException("No enabled generic-json sources are configured");
        }

        List<Map<String, Object>> sourceSummaries = sources.stream()
                .map(this::ingestSource)
                .toList();

        String status = globalStatus(sourceSummaries);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("sourceCount", sourceSummaries.size());
        result.put("fetchedCount", sum(sourceSummaries, "fetchedCount"));
        result.put("normalizedCount", sum(sourceSummaries, "normalizedCount"));
        result.put("processedCount", sum(sourceSummaries, "processedCount"));
        result.put("embeddedCount", sum(sourceSummaries, "embeddedCount"));
        result.put("indexedCount", sum(sourceSummaries, "indexedCount"));
        result.put("createdCount", sum(sourceSummaries, "createdCount"));
        result.put("updatedCount", sum(sourceSummaries, "updatedCount"));
        result.put("totalResults", sum(sourceSummaries, "totalResults"));
        result.put("positiveCount", sum(sourceSummaries, "positiveCount"));
        result.put("negativeCount", sum(sourceSummaries, "negativeCount"));
        result.put("neutralCount", sum(sourceSummaries, "neutralCount"));
        result.put("sources", sourceSummaries);

        LOG.info(
                "Ingestion finished with status {} across {} sources: {} fetched, {} normalized, {} processed, {} indexed",
                status,
                sourceSummaries.size(),
                result.get("fetchedCount"),
                result.get("normalizedCount"),
                result.get("processedCount"),
                result.get("indexedCount")
        );

        return result;
    }

    private Map<String, Object> ingestSource(SourceConfig source) {
        try {
            LOG.info("Source processing started: {}", source.getName());

            String rawJson = genericHttpNewsFetcher.fetch(source);
            List<NormalizedArticle> normalizedArticles = genericJsonArticleParser.parse(rawJson, source);
            int fetchedCount = normalizedArticles.size();
            int totalResults = normalizedArticles.size();
            List<ProcessedArticle> processedArticles = articleEnricher.enrich(normalizedArticles);
            IndexingSummary indexingSummary = articleIndexer.indexArticles(processedArticles);

            LOG.info(
                    "Source {} succeeded: fetched {}, normalized {}, enriched {}, indexed {} ({} created, {} updated)",
                    source.getName(),
                    fetchedCount,
                    normalizedArticles.size(),
                    processedArticles.size(),
                    indexingSummary.indexedCount(),
                    indexingSummary.createdCount(),
                    indexingSummary.updatedCount()
            );

            IngestionRunMetrics metrics = IngestionRunMetrics.successfulRun(
                    Instant.now(),
                    source.getName(),
                    "ok",
                    totalResults,
                    fetchedCount,
                    normalizedArticles.size(),
                    fetchedCount - normalizedArticles.size(),
                    indexingSummary.indexedCount(),
                    indexingSummary.createdCount(),
                    indexingSummary.updatedCount(),
                    processedArticles
            );

            metricsWriter.write(metrics);

            return sourceSummary(source.getName(), totalResults, fetchedCount, normalizedArticles.size(),
                    processedArticles.size(), metrics);
        } catch (Exception e) {
            String errorMessage = readableError(e);
            LOG.error("Source {} failed: {}", source.getName(), errorMessage, e);
            metricsWriter.write(failedMetrics(source.getName(), errorMessage));
            return failedSourceSummary(source.getName(), errorMessage);
        }
    }

    private Map<String, Object> sourceSummary(
            String sourceName,
            int totalResults,
            int fetchedCount,
            int normalizedCount,
            int processedCount,
            IngestionRunMetrics metrics
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceName);
        result.put("status", "ok");
        result.put("fetchedCount", fetchedCount);
        result.put("normalizedCount", normalizedCount);
        result.put("processedCount", processedCount);
        result.put("embeddedCount", metrics.embeddedCount());
        result.put("indexedCount", metrics.indexedCount());
        result.put("createdCount", metrics.createdCount());
        result.put("updatedCount", metrics.updatedCount());
        result.put("totalResults", totalResults);
        result.put("positiveCount", metrics.positiveCount());
        result.put("negativeCount", metrics.negativeCount());
        result.put("neutralCount", metrics.neutralCount());
        return result;
    }

    private Map<String, Object> failedSourceSummary(String sourceName, String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceName);
        result.put("status", "failed");
        result.put("fetchedCount", 0);
        result.put("normalizedCount", 0);
        result.put("processedCount", 0);
        result.put("embeddedCount", 0);
        result.put("indexedCount", 0);
        result.put("createdCount", 0);
        result.put("updatedCount", 0);
        result.put("totalResults", 0);
        result.put("positiveCount", 0);
        result.put("negativeCount", 0);
        result.put("neutralCount", 0);
        result.put("failedRequests", 1);
        result.put("errorMessage", errorMessage);
        return result;
    }

    private IngestionRunMetrics failedMetrics(String sourceName, String errorMessage) {
        return new IngestionRunMetrics(
                Instant.now(),
                sourceName,
                "FAILED",
                null,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                errorMessage
        );
    }

    private String globalStatus(List<Map<String, Object>> sourceSummaries) {
        long successfulSources = sourceSummaries.stream()
                .filter(summary -> "ok".equals(summary.get("status")))
                .count();

        if (successfulSources == sourceSummaries.size()) {
            return "ok";
        }

        if (successfulSources == 0) {
            return "failed";
        }

        return "partial";
    }

    private String readableError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }

    private boolean shouldIngestSource(SourceConfig source) {
        if (!source.isEnabled()) {
            LOG.info("Skipping disabled source {}", source.getName());
            return false;
        }

        if (!GENERIC_JSON_TYPE.equalsIgnoreCase(source.getType())) {
            LOG.warn(
                    "Skipping source {} because type '{}' is not supported. Supported type: {}",
                    source.getName(),
                    source.getType(),
                    GENERIC_JSON_TYPE
            );
            return false;
        }

        return true;
    }

    private int sum(List<Map<String, Object>> sourceSummaries, String field) {
        return sourceSummaries.stream()
                .map(summary -> summary.get(field))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .sum();
    }
}
