package com.ragnews.ingestion.fetcher;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.model.NewsApiResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Singleton
public class NewsApiFetcher {

    private static final String USER_AGENT = "news-rag-system/0.1 (Roberto Avila; local development)";
    private static final String API_KEY_PARAM = "apiKey";

    private final HttpClient httpClient;
    private final LocalEnvironmentResolver environmentResolver;

    public NewsApiFetcher(
            HttpClient httpClient,
            LocalEnvironmentResolver environmentResolver
    ) {
        this.httpClient = httpClient;
        this.environmentResolver = environmentResolver;
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
            return environmentResolver.resolveRequired(envName);
        }

        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
