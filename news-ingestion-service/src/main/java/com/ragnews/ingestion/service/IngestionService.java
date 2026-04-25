package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.fetcher.NewsApiFetcher;
import com.ragnews.ingestion.model.NewsApiResponse;
import com.ragnews.ingestion.parser.NewsApiArticleParser;
import com.ragnews.ingestion.parser.NormalizedArticle;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Singleton
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);

    private final SourceConfigurationLoader sourceConfigurationLoader;
    private final NewsApiFetcher newsApiFetcher;
    private final NewsApiArticleParser newsApiArticleParser;

    public IngestionService(
            SourceConfigurationLoader sourceConfigurationLoader,
            NewsApiFetcher newsApiFetcher,
            NewsApiArticleParser newsApiArticleParser
    ) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
        this.newsApiFetcher = newsApiFetcher;
        this.newsApiArticleParser = newsApiArticleParser;
    }

    public Map<String, Object> runIngestion() {
        List<SourceConfig> sources = sourceConfigurationLoader.loadSources();

        SourceConfig newsApiSource = sources.stream()
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

        return Map.of(
                "status", "ok",
                "source", newsApiSource.getName(),
                "fetchedCount", fetchedCount,
                "normalizedCount", normalizedArticles.size(),
                "totalResults", response.getTotalResults()
        );
    }
}
