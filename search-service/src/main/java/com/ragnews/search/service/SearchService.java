package com.ragnews.search.service;

import com.ragnews.search.embedding.EmbeddingGenerator;
import com.ragnews.search.model.SearchResult;
import com.ragnews.search.model.SearchResponse;
import com.ragnews.search.opensearch.OpenSearchSearchClient;
import jakarta.inject.Singleton;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SearchService {

    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingGenerator embeddingGenerator;
    private final OpenSearchSearchClient openSearchSearchClient;

    public SearchService(
            EmbeddingGenerator embeddingGenerator,
            OpenSearchSearchClient openSearchSearchClient
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.openSearchSearchClient = openSearchSearchClient;
    }

    public SearchResponse search(String query, int size) {
        return search(query, size, null);
    }

    public SearchResponse search(String query, int size, Double minScore) {
        List<Double> queryEmbedding = embeddingGenerator.generateEmbedding(query);
        LOG.info("Generated query embedding for '{}' with dimension {}", query, queryEmbedding.size());

        List<SearchResult> results = openSearchSearchClient.searchByEmbedding(queryEmbedding, size, minScore);
        return new SearchResponse(query, results.size(), minScore, results);
    }
}
