package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.enrichment.ArticleEnricher;
import com.ragnews.ingestion.fetcher.NewsApiFetcher;
import com.ragnews.ingestion.metrics.IngestionRunMetrics;
import com.ragnews.ingestion.metrics.MetricsWriter;
import com.ragnews.ingestion.model.NewsApiResponse;
import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.NewsApiArticleParser;
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

    private final SourceConfigurationLoader sourceConfigurationLoader;
    private final NewsApiFetcher newsApiFetcher;
    private final NewsApiArticleParser newsApiArticleParser;
    private final ArticleEnricher articleEnricher;
    private final ArticleIndexer articleIndexer;
    private final MetricsWriter metricsWriter;

    public IngestionService(
            SourceConfigurationLoader sourceConfigurationLoader,
            NewsApiFetcher newsApiFetcher,
            NewsApiArticleParser newsApiArticleParser,
            ArticleEnricher articleEnricher,
            ArticleIndexer articleIndexer,
            MetricsWriter metricsWriter
    ) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
        this.newsApiFetcher = newsApiFetcher;
        this.newsApiArticleParser = newsApiArticleParser;
        this.articleEnricher = articleEnricher;
        this.articleIndexer = articleIndexer;
        this.metricsWriter = metricsWriter;
    }

    public Map<String, Object> runIngestion() {
        List<SourceConfig> sources = sourceConfigurationLoader.loadSources().stream()
                .filter(this::isNewsApiCompatible)
                .toList();

        if (sources.isEmpty()) {
            throw new IllegalStateException("No NewsAPI-compatible sources are configured");
        }

        List<Map<String, Object>> sourceSummaries = sources.stream()
                .map(this::ingestSource)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
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
        return result;
    }

    private Map<String, Object> ingestSource(SourceConfig source) {
        try {
            NewsApiResponse response = newsApiFetcher.fetch(source);

            int fetchedCount = response.getArticles() == null
                    ? 0
                    : response.getArticles().size();

            List<NormalizedArticle> normalizedArticles =
                    newsApiArticleParser.parse(response, source.getName());
            List<ProcessedArticle> processedArticles = articleEnricher.enrich(normalizedArticles);
            IndexingSummary indexingSummary = articleIndexer.indexArticles(processedArticles);

            LOG.info(
                    "Fetched {} articles from {}, normalized {} articles, enriched {} articles, indexed {} articles ({} created, {} updated)",
                    fetchedCount,
                    source.getName(),
                    normalizedArticles.size(),
                    processedArticles.size(),
                    indexingSummary.indexedCount(),
                    indexingSummary.createdCount(),
                    indexingSummary.updatedCount()
            );

            IngestionRunMetrics metrics = IngestionRunMetrics.successfulRun(
                    Instant.now(),
                    source.getName(),
                    response.getStatus(),
                    response.getTotalResults(),
                    fetchedCount,
                    normalizedArticles.size(),
                    fetchedCount - normalizedArticles.size(),
                    indexingSummary.indexedCount(),
                    indexingSummary.createdCount(),
                    indexingSummary.updatedCount(),
                    processedArticles
            );

            metricsWriter.write(metrics);

            return sourceSummary(source.getName(), response.getTotalResults(), fetchedCount, normalizedArticles.size(),
                    processedArticles.size(), metrics);
        } catch (Exception e) {
            LOG.error("Ingestion run failed for source {}", source.getName(), e);
            metricsWriter.write(failedMetrics(source.getName()));
            throw e;
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

    private IngestionRunMetrics failedMetrics(String sourceName) {
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
                0
        );
    }

    private boolean isNewsApiCompatible(SourceConfig source) {
        return source.getUrl() != null && source.getUrl().toLowerCase().contains("newsapi.org");
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
