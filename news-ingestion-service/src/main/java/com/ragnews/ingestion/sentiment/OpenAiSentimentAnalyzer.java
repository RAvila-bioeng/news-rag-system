package com.ragnews.ingestion.sentiment;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.parser.NormalizedArticle;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Primary
@Singleton
@Requires(property = "sentiment.provider", value = "openai")
public class OpenAiSentimentAnalyzer implements SentimentAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiSentimentAnalyzer.class);
    private static final URI CHAT_COMPLETIONS_URI = URI.create("https://api.openai.com/v1/chat/completions");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final LocalEnvironmentResolver environmentResolver;
    private final SimpleKeywordSentimentAnalyzer fallbackAnalyzer;
    private final String model;
    private final int maxTextChars;
    private final String configuredApiKey;
    private final OpenAiHttpSender httpSender;

    public OpenAiSentimentAnalyzer(
            ObjectMapper objectMapper,
            LocalEnvironmentResolver environmentResolver,
            SimpleKeywordSentimentAnalyzer fallbackAnalyzer,
            @Value("${sentiment.model}") String model,
            @Value("${sentiment.max-text-chars}") int maxTextChars,
            @Value("${sentiment.api-key:}") String apiKey
    ) {
        this(
                objectMapper,
                environmentResolver,
                fallbackAnalyzer,
                model,
                maxTextChars,
                apiKey,
                defaultSender()
        );
    }

    OpenAiSentimentAnalyzer(
            ObjectMapper objectMapper,
            LocalEnvironmentResolver environmentResolver,
            SimpleKeywordSentimentAnalyzer fallbackAnalyzer,
            String model,
            int maxTextChars,
            String apiKey,
            OpenAiHttpSender httpSender
    ) {
        if (maxTextChars <= 0) {
            throw new IllegalArgumentException("sentiment.max-text-chars must be greater than zero");
        }

        this.objectMapper = objectMapper;
        this.environmentResolver = environmentResolver;
        this.fallbackAnalyzer = fallbackAnalyzer;
        this.model = model;
        this.maxTextChars = maxTextChars;
        this.configuredApiKey = apiKey;
        this.httpSender = httpSender;
    }

    @Override
    public Sentiment analyze(NormalizedArticle article) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(article, "OpenAI sentiment provider is enabled, but OPENAI_API_KEY is missing");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(article));
            HttpRequest request = HttpRequest.newBuilder(CHAT_COMPLETIONS_URI)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback(
                        article,
                        "OpenAI sentiment request failed with status "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            return parseSentiment(response.body())
                    .orElseGet(() -> fallback(article, "OpenAI sentiment response was not valid JSON sentiment"));
        } catch (IOException e) {
            return fallback(article, "Could not serialize or send OpenAI sentiment request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback(article, "OpenAI sentiment request was interrupted", e);
        } catch (RuntimeException e) {
            return fallback(article, "OpenAI sentiment request failed", e);
        }
    }

    private String resolveApiKey() {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey;
        }

        return environmentResolver.resolve("OPENAI_API_KEY");
    }

    private static OpenAiHttpSender defaultSender() {
        HttpClient httpClient = HttpClient.newHttpClient();
        return request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> buildRequest(NormalizedArticle article) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", 0);
        request.put("max_tokens", 20);
        request.put("response_format", Map.of("type", "json_object"));
        request.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Classify news sentiment as positive, negative, or neutral. Return only JSON."
                ),
                Map.of(
                        "role", "user",
                        "content", "Return exactly this JSON shape: {\"sentiment\":\"positive|negative|neutral\"}\n\n"
                                + truncate(buildText(article))
                )
        ));
        return request;
    }

    private Optional<Sentiment> parseSentiment(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(
                    responseBody,
                    Argument.mapOf(String.class, Object.class)
            );
            List<Object> choices = asList(response.get("choices"));
            if (choices.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> firstChoice = asMap(choices.get(0));
            Map<String, Object> message = asMap(firstChoice.get("message"));
            Object content = message.get("content");
            if (!(content instanceof String jsonContent)) {
                return Optional.empty();
            }

            Map<String, Object> sentimentJson = objectMapper.readValue(
                    jsonContent,
                    Argument.mapOf(String.class, Object.class)
            );
            Object sentiment = sentimentJson.get("sentiment");
            if (!(sentiment instanceof String value)) {
                return Optional.empty();
            }

            return toSentiment(value);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<Sentiment> toSentiment(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "positive" -> Optional.of(Sentiment.POSITIVE);
            case "negative" -> Optional.of(Sentiment.NEGATIVE);
            case "neutral" -> Optional.of(Sentiment.NEUTRAL);
            default -> Optional.empty();
        };
    }

    private String buildText(NormalizedArticle article) {
        String title = article.title() == null ? "" : article.title();
        String content = article.content() == null ? "" : article.content();
        String text = (title + "\n\n" + content).trim();

        if (text.isBlank()) {
            return "untitled news article";
        }

        return text;
    }

    private String truncate(String text) {
        if (text.length() <= maxTextChars) {
            return text;
        }

        return text.substring(0, maxTextChars);
    }

    private Sentiment fallback(NormalizedArticle article, String reason) {
        LOG.warn("{}; using simple keyword sentiment fallback", reason);
        return fallbackAnalyzer.analyze(article);
    }

    private Sentiment fallback(NormalizedArticle article, String reason, Throwable throwable) {
        LOG.warn("{}; using simple keyword sentiment fallback", reason, throwable);
        return fallbackAnalyzer.analyze(article);
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

    @FunctionalInterface
    interface OpenAiHttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }
}
