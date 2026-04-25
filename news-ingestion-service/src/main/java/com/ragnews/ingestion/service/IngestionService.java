package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.fetcher.NewsApiFetcher;
import com.ragnews.ingestion.metrics.IngestionRunMetrics;
import com.ragnews.ingestion.metrics.MetricsWriter;
import com.ragnews.ingestion.model.NewsApiResponse;
import com.ragnews.ingestion.parser.NewsApiArticleParser;
import com.ragnews.ingestion.parser.NormalizedArticle;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Singleton
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);

    private final SourceConfigurationLoader sourceConfigurationLoader;
    private final NewsApiFetcher newsApiFetcher;
    private final NewsApiArticleParser newsApiArticleParser;
    private final MetricsWriter metricsWriter;

    public IngestionService(
            SourceConfigurationLoader sourceConfigurationLoader,
            NewsApiFetcher newsApiFetcher,
            NewsApiArticleParser newsApiArticleParser,
            MetricsWriter metricsWriter
    ) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
        this.newsApiFetcher = newsApiFetcher;
        this.newsApiArticleParser = newsApiArticleParser;
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

            LOG.info(
                    "Fetched {} articles from {} and normalized {} articles",
                    fetchedCount,
                    newsApiSource.getName(),
                    normalizedArticles.size()
            );

            metricsWriter.write(new IngestionRunMetrics(
                    Instant.now(),
                    newsApiSource.getName(),
                    "SUCCESS",
                    response.getStatus(),
                    response.getTotalResults(),
                    fetchedCount,
                    normalizedArticles.size(),
                    fetchedCount - normalizedArticles.size(),
                    0,
                    0,
                    0,
                    0
            ));

            return Map.of(
                    "status", "ok",
                    "source", newsApiSource.getName(),
                    "fetchedCount", fetchedCount,
                    "normalizedCount", normalizedArticles.size(),
                    "totalResults", response.getTotalResults()
            );
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
                    0
            ));

            throw e;
        }
    }
}
