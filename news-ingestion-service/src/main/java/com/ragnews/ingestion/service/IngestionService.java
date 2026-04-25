package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.fetcher.NewsApiFetcher;
import com.ragnews.ingestion.model.NewsApiResponse;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class IngestionService {

    private final SourceConfigurationLoader sourceConfigurationLoader;
    private final NewsApiFetcher newsApiFetcher;

    public IngestionService(
            SourceConfigurationLoader sourceConfigurationLoader,
            NewsApiFetcher newsApiFetcher
    ) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
        this.newsApiFetcher = newsApiFetcher;
    }

    public Map<String, String> runIngestion() {
        List<SourceConfig> sources = sourceConfigurationLoader.loadSources();

        SourceConfig newsApiSource = sources.stream()
                .filter(source -> "NewsAPI".equalsIgnoreCase(source.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NewsAPI source is not configured"));

        NewsApiResponse response = newsApiFetcher.fetch(newsApiSource);

        int fetchedCount = response.getArticles() == null
                ? 0
                : response.getArticles().size();

        return Map.of(
                "status", "ok",
                "source", newsApiSource.getName(),
                "fetchedCount", String.valueOf(fetchedCount),
                "totalResults", String.valueOf(response.getTotalResults())
        );
    }
}