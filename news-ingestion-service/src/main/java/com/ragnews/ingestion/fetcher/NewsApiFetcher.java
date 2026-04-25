package com.ragnews.ingestion.fetcher;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.model.NewsApiResponse;
import io.github.cdimascio.dotenv.Dotenv;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Singleton
public class NewsApiFetcher {

    private static final String USER_AGENT = "news-rag-system/0.1 (Roberto Avila; local development)";
    private static final String API_KEY_PARAM = "apiKey";

    private final HttpClient httpClient;

    public NewsApiFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public NewsApiResponse fetch(SourceConfig sourceConfig) {
        String requestUrl = buildRequestUrl(sourceConfig);
        String apiKey = resolveApiKey(sourceConfig);
        BlockingHttpClient blockingClient = httpClient.toBlocking();

        HttpRequest<?> request = HttpRequest.GET(URI.create(requestUrl))
                .header("User-Agent", USER_AGENT)
                .header("X-Api-Key", apiKey);
        return blockingClient.retrieve(request, NewsApiResponse.class);
    }

    private String buildRequestUrl(SourceConfig sourceConfig) {
        StringBuilder urlBuilder = new StringBuilder(sourceConfig.getUrl());

        if (sourceConfig.getParams() == null || sourceConfig.getParams().isEmpty()) {
            return urlBuilder.toString();
        }

        boolean first = true;

        for (Map.Entry<String, String> param : sourceConfig.getParams().entrySet()) {
            if (API_KEY_PARAM.equalsIgnoreCase(param.getKey())) {
                continue;
            }

            if (!first) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
            }

            urlBuilder.append(encode(param.getKey()));
            urlBuilder.append("=");
            urlBuilder.append(encode(resolveEnvPlaceholder(param.getValue())));

            first = false;
        }

        return urlBuilder.toString();
    }

    private String resolveApiKey(SourceConfig sourceConfig) {
        if (sourceConfig.getParams() != null) {
            String configuredApiKey = sourceConfig.getParams().get(API_KEY_PARAM);
            if (configuredApiKey != null && !configuredApiKey.isBlank()) {
                return resolveEnvPlaceholder(configuredApiKey);
            }
        }

        return resolveEnvPlaceholder("${NEWS_API_KEY}");
    }

    private String resolveEnvPlaceholder(String value) {
        if (value == null) {
            return "";
        }

        if (value.startsWith("${") && value.endsWith("}")) {
            String envName = value.substring(2, value.length() - 1);

            String envValue = System.getenv(envName);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }

            Dotenv dotenv = Dotenv.configure()
                    .directory(findRepoRoot().toString())
                    .ignoreIfMissing()
                    .load();

            String dotenvValue = dotenv.get(envName);
            if (dotenvValue != null && !dotenvValue.isBlank()) {
                return dotenvValue;
            }

            throw new IllegalStateException("Missing environment variable or .env value: " + envName);
        }

        return value;
    }

    private Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve(".env")) ||
                    Files.exists(current.resolve("config").resolve("sources.yaml"))) {
                return current;
            }

            current = current.getParent();
        }

        return Path.of("").toAbsolutePath();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
