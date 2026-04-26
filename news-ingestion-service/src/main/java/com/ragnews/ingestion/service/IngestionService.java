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
        SourceConfig newsApiSource = null;

        try {
            List<SourceConfig> sources = sourceConfigurationLoader.loadSources();

            newsApiSource = sources.stream()
                    .filter(source -> "NewsAPI".equalsIgnoreCase(source.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("NewsAPI source is not configured"));

            NewsApiResponse response = newsApiFetcher.fetch(newsApiSource);

            int fetchedCount = response.getArticles() == null
                    ? 0
                    : response.getArticles().size();

            List<NormalizedArticle> normalizedArticles =
                    newsApiArticleParser.parse(response, newsApiSource.getName());
            List<ProcessedArticle> processedArticles = articleEnricher.enrich(normalizedArticles);
            int indexedCount = articleIndexer.indexArticles(processedArticles);

            LOG.info(
                    "Fetched {} articles from {}, normalized {} articles, enriched {} articles, and indexed {} articles",
                    fetchedCount,
                    newsApiSource.getName(),
                    normalizedArticles.size(),
                    processedArticles.size(),
                    indexedCount
            );

            IngestionRunMetrics metrics = IngestionRunMetrics.successfulRun(
                    Instant.now(),
                    newsApiSource.getName(),
                    response.getStatus(),
                    response.getTotalResults(),
                    fetchedCount,
                    normalizedArticles.size(),
                    fetchedCount - normalizedArticles.size(),
                    indexedCount,
                    processedArticles
            );

            metricsWriter.write(metrics);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("source", newsApiSource.getName());
            result.put("fetchedCount", fetchedCount);
            result.put("normalizedCount", normalizedArticles.size());
            result.put("processedCount", processedArticles.size());
            result.put("embeddedCount", metrics.embeddedCount());
            result.put("indexedCount", metrics.indexedCount());
            result.put("totalResults", response.getTotalResults());
            result.put("positiveCount", metrics.positiveCount());
            result.put("negativeCount", metrics.negativeCount());
            result.put("neutralCount", metrics.neutralCount());
            return result;
        } catch (Exception e) {
            String sourceName = newsApiSource == null ? "NewsAPI" : newsApiSource.getName();
            LOG.error("Ingestion run failed for source {}", sourceName, e);

            metricsWriter.write(new IngestionRunMetrics(
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
                    0
            ));

            throw e;
        }
    }
}
