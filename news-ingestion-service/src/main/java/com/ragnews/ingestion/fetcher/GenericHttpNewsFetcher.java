package com.ragnews.ingestion.fetcher;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.config.SourceConfig;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Singleton
public class GenericHttpNewsFetcher {

    private static final String DEFAULT_METHOD = "GET";

    private final HttpClient httpClient;
    private final LocalEnvironmentResolver environmentResolver;

    public GenericHttpNewsFetcher(
            HttpClient httpClient,
            LocalEnvironmentResolver environmentResolver
    ) {
        this.httpClient = httpClient;
        this.environmentResolver = environmentResolver;
    }

    public String fetch(SourceConfig sourceConfig) {
        String method = method(sourceConfig);
        if (!DEFAULT_METHOD.equalsIgnoreCase(method)) {
            throw new IllegalArgumentException(
                    "Unsupported HTTP method for source " + sourceConfig.getName() +
                            ": " + method + ". Only GET is supported for now."
            );
        }

        String requestUrl = buildRequestUrl(sourceConfig);
        BlockingHttpClient blockingClient = httpClient.toBlocking();
        MutableHttpRequest<?> request = HttpRequest.GET(URI.create(requestUrl));

        applyHeaders(request, sourceConfig);

        return blockingClient.retrieve(request, String.class);
    }

    private String buildRequestUrl(SourceConfig sourceConfig) {
        StringBuilder urlBuilder = new StringBuilder(sourceConfig.getUrl());

        if (sourceConfig.getParams() == null || sourceConfig.getParams().isEmpty()) {
            return urlBuilder.toString();
        }

        boolean hasQuery = sourceConfig.getUrl().contains("?");
        boolean first = !hasQuery;

        for (Map.Entry<String, String> param : sourceConfig.getParams().entrySet()) {
            if (first) {
                urlBuilder.append("?");
                first = false;
            } else {
                urlBuilder.append("&");
            }

            urlBuilder.append(encode(param.getKey()));
            urlBuilder.append("=");
            urlBuilder.append(encode(resolvePlaceholders(param.getValue())));
        }

        return urlBuilder.toString();
    }

    private void applyHeaders(MutableHttpRequest<?> request, SourceConfig sourceConfig) {
        if (sourceConfig.getHeaders() == null || sourceConfig.getHeaders().isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> header : sourceConfig.getHeaders().entrySet()) {
            request.header(header.getKey(), resolvePlaceholders(header.getValue()));
        }
    }

    private String method(SourceConfig sourceConfig) {
        if (sourceConfig.getMethod() == null || sourceConfig.getMethod().isBlank()) {
            return DEFAULT_METHOD;
        }

        return sourceConfig.getMethod();
    }

    private String resolvePlaceholders(String value) {
        if (value == null) {
            return "";
        }

        int placeholderStart = value.indexOf("${");
        while (placeholderStart >= 0) {
            int placeholderEnd = value.indexOf("}", placeholderStart);
            if (placeholderEnd < 0) {
                return value;
            }

            String envName = value.substring(placeholderStart + 2, placeholderEnd);
            String resolvedValue = environmentResolver.resolveRequired(envName);
            value = value.substring(0, placeholderStart) +
                    resolvedValue +
                    value.substring(placeholderEnd + 1);
            placeholderStart = value.indexOf("${", placeholderStart + resolvedValue.length());
        }

        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
