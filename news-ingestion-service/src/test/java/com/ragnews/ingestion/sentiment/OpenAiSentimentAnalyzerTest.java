package com.ragnews.ingestion.sentiment;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.parser.NormalizedArticle;
import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSentimentAnalyzerTest {

    @Test
    void mapsPositiveJsonCorrectly() {
        OpenAiSentimentAnalyzer analyzer = analyzerReturning("positive");

        assertEquals(Sentiment.POSITIVE, analyzer.analyze(article("Company reports strong profit growth", "")));
    }

    @Test
    void mapsNegativeJsonCorrectly() {
        OpenAiSentimentAnalyzer analyzer = analyzerReturning("negative");

        assertEquals(Sentiment.NEGATIVE, analyzer.analyze(article("Markets remain steady", "")));
    }

    @Test
    void mapsNeutralJsonCorrectly() {
        OpenAiSentimentAnalyzer analyzer = analyzerReturning("neutral");

        assertEquals(Sentiment.NEUTRAL, analyzer.analyze(article("Company announces board meeting", "")));
    }

    @Test
    void invalidJsonTriggersFallback() {
        OpenAiSentimentAnalyzer analyzer = analyzerWithResponse("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}");

        assertEquals(Sentiment.POSITIVE, analyzer.analyze(article("Company reports strong profit growth", "")));
    }

    @Test
    void missingApiKeyTriggersFallback() {
        OpenAiSentimentAnalyzer analyzer = new OpenAiSentimentAnalyzer(
                objectMapper(),
                environmentResolver(null),
                new SimpleKeywordSentimentAnalyzer(),
                "gpt-4.1-mini",
                3000,
                "",
                request -> {
                    throw new AssertionError("OpenAI should not be called when the API key is missing");
                }
        );

        assertEquals(Sentiment.NEGATIVE, analyzer.analyze(article("Shares fall after inflation risks rise", "")));
    }

    @Test
    void simpleProviderUsesSimpleAnalyzer() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("sentiment.provider", "simple"))) {
            SentimentAnalyzer analyzer = context.getBean(SentimentAnalyzer.class);

            assertInstanceOf(SimpleKeywordSentimentAnalyzer.class, analyzer);
        }
    }

    @Test
    void openAiProviderUsesPrimaryOpenAiAnalyzerAndKeepsSimpleFallbackAvailable() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("sentiment.provider", "openai"))) {
            SentimentAnalyzer analyzer = context.getBean(SentimentAnalyzer.class);

            assertInstanceOf(OpenAiSentimentAnalyzer.class, analyzer);
            assertTrue(context.containsBean(SimpleKeywordSentimentAnalyzer.class));
        }
    }

    private OpenAiSentimentAnalyzer analyzerReturning(String sentiment) {
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"sentiment\\\":\\\"" + sentiment + "\\\"}\"}}]}";
        return analyzerWithResponse(body);
    }

    private OpenAiSentimentAnalyzer analyzerWithResponse(String body) {
        return new OpenAiSentimentAnalyzer(
                objectMapper(),
                environmentResolver("test-api-key"),
                new SimpleKeywordSentimentAnalyzer(),
                "gpt-4.1-mini",
                3000,
                "",
                request -> new FakeHttpResponse(200, body, request)
        );
    }

    private ObjectMapper objectMapper() {
        try (ApplicationContext context = ApplicationContext.run()) {
            return context.getBean(ObjectMapper.class);
        }
    }

    private LocalEnvironmentResolver environmentResolver(String apiKey) {
        return new LocalEnvironmentResolver() {
            @Override
            public String resolve(String name) {
                if ("OPENAI_API_KEY".equals(name)) {
                    return apiKey;
                }

                return null;
            }
        };
    }

    private NormalizedArticle article(String title, String content) {
        return new NormalizedArticle(
                "external-1",
                title,
                content,
                "Test Source",
                "Test Author",
                "https://example.com/article",
                Instant.parse("2026-05-09T10:00:00Z")
        );
    }

    private record FakeHttpResponse(
            int statusCode,
            String body,
            HttpRequest request
    ) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
