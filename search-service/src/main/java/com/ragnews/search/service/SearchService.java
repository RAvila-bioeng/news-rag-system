package com.ragnews.search.service;

import com.ragnews.search.embedding.EmbeddingGenerator;
import com.ragnews.search.model.SearchResult;
import com.ragnews.search.model.SearchResponse;
import com.ragnews.search.opensearch.OpenSearchSearchClient;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SearchService {

    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingGenerator embeddingGenerator;
    private final OpenSearchSearchClient openSearchSearchClient;
    private final int defaultTopK;

    public SearchService(
            EmbeddingGenerator embeddingGenerator,
            OpenSearchSearchClient openSearchSearchClient,
            @Value("${search.default-top-k:5}") int defaultTopK
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.openSearchSearchClient = openSearchSearchClient;
        this.defaultTopK = defaultTopK;
    }

    public SearchResponse search(String query) {
        List<Double> queryEmbedding = embeddingGenerator.generateEmbedding(query);
        LOG.info("Generated query embedding for '{}' with dimension {}", query, queryEmbedding.size());

        List<SearchResult> results = openSearchSearchClient.searchByEmbedding(queryEmbedding, defaultTopK);
        return new SearchResponse(query, results.size(), results);
    }
}
