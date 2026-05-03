package com.ragnews.search.controller;

import com.ragnews.search.model.SearchResponse;
import com.ragnews.search.service.SearchService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.util.Map;
import java.util.Optional;

@Controller("/search")
public class SearchController {

    private static final int DEFAULT_SIZE = 5;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 20;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Get
    public HttpResponse<?> search(
            @QueryValue Optional<String> q,
            @QueryValue Optional<Integer> size,
            @QueryValue Optional<Double> minScore
    ) {
        if (q.isEmpty() || q.get().isBlank()) {
            return HttpResponse.badRequest(Map.of("message", "Query parameter 'q' is required"));
        }

        int requestedSize = size.orElse(DEFAULT_SIZE);
        if (requestedSize < MIN_SIZE || requestedSize > MAX_SIZE) {
            return HttpResponse.badRequest(Map.of("message", "Query parameter 'size' must be between 1 and 20"));
        }

        if (minScore.isPresent() && minScore.get() < 0) {
            return HttpResponse.badRequest(Map.of("message", "Query parameter 'minScore' must be greater than or equal to 0"));
        }

        return HttpResponse.ok(searchService.search(q.get(), requestedSize, minScore.orElse(null)));
    }
}
