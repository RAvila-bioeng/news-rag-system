package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class IngestionService {

    private final SourceConfigurationLoader sourceConfigurationLoader;

    public IngestionService(SourceConfigurationLoader sourceConfigurationLoader) {
        this.sourceConfigurationLoader = sourceConfigurationLoader;
    }

    public Map<String, String> runIngestion() {
        List<SourceConfig> sources = sourceConfigurationLoader.loadSources();

        String sourceNames = sources.stream()
                .map(SourceConfig::getName)
                .collect(Collectors.joining(", "));

        return Map.of(
                "status", "ok",
                "sourcesLoaded", String.valueOf(sources.size()),
                "sources", sourceNames
        );
    }
}