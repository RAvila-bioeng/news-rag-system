package com.ragnews.search.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ragnews.search.model.SearchResponse;
import com.ragnews.search.service.SearchService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchControllerTest {

    @Test
    void searchWithoutMinScoreKeepsCurrentBehavior() {
        CapturingSearchService searchService = new CapturingSearchService();
        SearchController controller = new SearchController(searchService);

        HttpResponse<?> response = controller.search(Optional.of("healthcare"), Optional.of(10), Optional.empty());

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("healthcare", searchService.query);
        assertEquals(10, searchService.size);
        assertNull(searchService.minScore);
        assertSame(searchService.response, response.body());
    }

    @Test
    void searchWithValidMinScorePassesValueToService() {
        CapturingSearchService searchService = new CapturingSearchService();
        SearchController controller = new SearchController(searchService);

        HttpResponse<?> response = controller.search(Optional.of("healthcare"), Optional.of(10), Optional.of(0.39));

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(0.39, searchService.minScore);
        assertSame(searchService.response, response.body());
    }

    @Test
    void searchWithNegativeMinScoreReturnsBadRequest() {
        CapturingSearchService searchService = new CapturingSearchService();
        SearchController controller = new SearchController(searchService);

        HttpResponse<?> response = controller.search(Optional.of("healthcare"), Optional.of(10), Optional.of(-0.1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals(0, searchService.calls);
    }

    @Test
    void searchWithInvalidSizeReturnsBadRequest() {
        CapturingSearchService searchService = new CapturingSearchService();
        SearchController controller = new SearchController(searchService);

        HttpResponse<?> response = controller.search(Optional.of("healthcare"), Optional.of(21), Optional.empty());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals(0, searchService.calls);
    }

    private static class CapturingSearchService extends SearchService {
        private final SearchResponse response = new SearchResponse("healthcare", 0, null, List.of());
        private String query;
        private int size;
        private Double minScore;
        private int calls;

        private CapturingSearchService() {
            super(null, null);
        }

        @Override
        public SearchResponse search(String query, int size, Double minScore) {
            this.query = query;
            this.size = size;
            this.minScore = minScore;
            this.calls++;
            return response;
        }
    }
}
