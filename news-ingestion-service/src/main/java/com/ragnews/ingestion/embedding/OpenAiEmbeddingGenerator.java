package com.ragnews.ingestion.embedding;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.parser.NormalizedArticle;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Requires(property = "embedding.provider", value = "openai")
public class OpenAiEmbeddingGenerator implements EmbeddingGenerator {

    private static final URI EMBEDDINGS_URI = URI.create("https://api.openai.com/v1/embeddings");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalEnvironmentResolver environmentResolver;
    private final String model;
    private final int dimensions;
    private final String configuredApiKey;

    public OpenAiEmbeddingGenerator(
            ObjectMapper objectMapper,
            LocalEnvironmentResolver environmentResolver,
            @Value("${embedding.model}") String model,
            @Value("${embedding.dimensions}") int dimensions,
            @Value("${embedding.api-key:}") String apiKey
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.environmentResolver = environmentResolver;
        this.model = model;
        this.dimensions = dimensions;
        this.configuredApiKey = apiKey;
    }

    @Override
    public List<Double> generate(NormalizedArticle article) {
        return createEmbedding(buildText(article));
    }

    private List<Double> createEmbedding(String text) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI embedding provider is enabled, but OPENAI_API_KEY is missing"
            );
        }

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(text));
            HttpRequest request = HttpRequest.newBuilder(EMBEDDINGS_URI)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "OpenAI embeddings request failed with status "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            return parseEmbedding(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize or send OpenAI embeddings request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI embeddings request was interrupted", e);
        }
    }

    private String resolveApiKey() {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey;
        }

        return environmentResolver.resolve("OPENAI_API_KEY");
    }

    private Map<String, Object> buildRequest(String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("input", text);
        request.put("dimensions", dimensions);
        request.put("encoding_format", "float");
        return request;
    }

    private List<Double> parseEmbedding(String responseBody) throws IOException {
        Map<String, Object> response = objectMapper.readValue(
                responseBody,
                Argument.mapOf(String.class, Object.class)
        );
        List<Object> data = asList(response.get("data"));
        if (data.isEmpty()) {
            throw new RuntimeException("OpenAI embeddings response did not contain any embedding data");
        }

        Map<String, Object> firstEmbedding = asMap(data.get(0));
        List<Object> rawEmbedding = asList(firstEmbedding.get("embedding"));
        List<Double> embedding = rawEmbedding.stream()
                .map(this::asDouble)
                .toList();

        if (embedding.size() != dimensions) {
            throw new RuntimeException(
                    "OpenAI returned embedding dimension "
                            + embedding.size()
                            + " but application.yml expects "
                            + dimensions
            );
        }

        return embedding;
    }

    private String buildText(NormalizedArticle article) {
        String title = article.title() == null ? "" : article.title();
        String content = article.content() == null ? "" : article.content();
        String text = (title + " " + content).trim();

        if (text.isBlank()) {
            return "untitled news article";
        }

        return text;
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

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        throw new RuntimeException("OpenAI embedding response contained a non-numeric vector value");
    }
}
