package com.ragnews.search.opensearch;

import com.ragnews.search.model.SearchResult;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class OpenSearchSearchClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI searchUri;

    public OpenSearchSearchClient(
            ObjectMapper objectMapper,
            @Value("${opensearch.url:`http://localhost:9200`}") String openSearchUrl,
            @Value("${opensearch.index:`news_article`}") String indexName
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.searchUri = URI.create(openSearchUrl + "/" + indexName + "/_search");
    }

    public List<SearchResult> searchByEmbedding(List<Double> embedding, int topK) {
        return searchByEmbedding(embedding, topK, null);
    }

    public List<SearchResult> searchByEmbedding(List<Double> embedding, int topK, Double minScore) {
        try {
            String requestBody = objectMapper.writeValueAsString(buildSearchRequest(embedding, topK));
            HttpRequest request = HttpRequest.newBuilder(searchUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "OpenSearch search failed with status "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            List<SearchResult> results = parseSearchResults(response.body());
            return filterByMinScore(results, minScore);
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize or send OpenSearch search request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenSearch search request was interrupted", e);
        }
    }

    private Map<String, Object> buildSearchRequest(List<Double> embedding, int topK) {
        Map<String, Object> embeddingQuery = new LinkedHashMap<>();
        embeddingQuery.put("vector", embedding);
        embeddingQuery.put("k", topK);

        Map<String, Object> knnFields = new LinkedHashMap<>();
        knnFields.put("embedding", embeddingQuery);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("knn", knnFields);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", topK);
        request.put("_source", List.of("title", "source", "url", "sentiment", "timestamp"));
        request.put("query", query);
        return request;
    }

    private List<SearchResult> parseSearchResults(String responseBody) throws IOException {
        Map<String, Object> response = objectMapper.readValue(
                responseBody,
                Argument.mapOf(String.class, Object.class)
        );
        Map<String, Object> hitsContainer = asMap(response.get("hits"));
        List<Object> hits = asList(hitsContainer.get("hits"));
        List<SearchResult> results = new ArrayList<>(hits.size());

        for (Object hitObject : hits) {
            Map<String, Object> hit = asMap(hitObject);
            Map<String, Object> source = asMap(hit.get("_source"));
            results.add(new SearchResult(
                    asString(source.get("title")),
                    asString(source.get("source")),
                    asString(source.get("url")),
                    asString(source.get("sentiment")),
                    asString(source.get("timestamp")),
                    asDouble(hit.get("_score"))
            ));
        }

        return results;
    }

    private List<SearchResult> filterByMinScore(List<SearchResult> results, Double minScore) {
        if (minScore == null) {
            return results;
        }

        List<SearchResult> filteredResults = new ArrayList<>();
        for (SearchResult result : results) {
            if (result.score() >= minScore) {
                filteredResults.add(result);
            }
        }

        return filteredResults;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }

        return List.of();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return 0.0;
    }
}
