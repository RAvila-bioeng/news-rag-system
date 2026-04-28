package com.ragnews.search.controller;

import com.ragnews.search.model.SearchResponse;
import com.ragnews.search.service.SearchService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.util.Optional;

@Controller("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Get
    public HttpResponse<SearchResponse> search(@QueryValue Optional<String> q) {
        if (q.isEmpty() || q.get().isBlank()) {
            return HttpResponse.badRequest();
        }

        return HttpResponse.ok(searchService.search(q.get()));
    }
}
