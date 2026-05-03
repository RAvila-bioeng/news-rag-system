package com.ragnews.search.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record SearchResponse(
        String query,
        int resultCount,
        Double minScore,
        List<SearchResult> results
) {
    public SearchResponse(String query, int resultCount, List<SearchResult> results) {
        this(query, resultCount, null, results);
    }

    public SearchResponse {
        if (results == null) {
            results = List.of();
        }
    }
}
