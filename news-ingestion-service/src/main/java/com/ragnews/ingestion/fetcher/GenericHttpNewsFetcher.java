package com.ragnews.ingestion.fetcher;

import com.ragnews.ingestion.config.LocalEnvironmentResolver;
import com.ragnews.ingestion.config.SourceConfig;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Singleton
public class GenericHttpNewsFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(GenericHttpNewsFetcher.class);
    private static final String DEFAULT_METHOD = "GET";
    private static final List<Integer> RETRYABLE_STATUS_CODES = List.of(429, 500, 502, 503, 504);

    private final HttpClient httpClient;
    private final LocalEnvironmentResolver environmentResolver;
    private final int maxRetries;
    private final long retryDelayMs;

    public GenericHttpNewsFetcher(
            HttpClient httpClient,
            LocalEnvironmentResolver environmentResolver,
            @Value("${ingestion.http.max-retries:3}") int maxRetries,
            @Value("${ingestion.http.retry-delay-ms:500}") long retryDelayMs
    ) {
        this.httpClient = httpClient;
        this.environmentResolver = environmentResolver;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryDelayMs = Math.max(0, retryDelayMs);
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
        MutableHttpRequest<?> request = HttpRequest.GET(URI.create(requestUrl));

        applyHeaders(request, sourceConfig);

        return retrieveWithRetries(sourceConfig, request);
    }

    private String retrieveWithRetries(
            SourceConfig sourceConfig,
            MutableHttpRequest<?> request
    ) {
        int maxAttempts = maxRetries + 1;
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return retrieve(request);
            } catch (Exception e) {
                lastFailure = e;

                if (!isRetryable(e) || attempt == maxAttempts) {
                    throw fetchFailure(sourceConfig, e);
                }

                LOG.warn(
                        "Transient HTTP fetch failure for source {} on attempt {}/{}. Retrying in {} ms: {}",
                        sourceConfig.getName(),
                        attempt,
                        maxAttempts,
                        retryDelayMs,
                        conciseError(e)
                );
                sleepBeforeRetry(sourceConfig, attempt);
            }
        }

        throw fetchFailure(sourceConfig, lastFailure);
    }

    protected String retrieve(MutableHttpRequest<?> request) {
        return httpClient.toBlocking().retrieve(request, String.class);
    }

    private RuntimeException fetchFailure(SourceConfig sourceConfig, Exception failure) {
        String message = "HTTP fetch failed for source " + sourceConfig.getName() + ": " + conciseError(failure);
        return new IllegalStateException(message, failure);
    }

    private boolean isRetryable(Exception failure) {
        if (failure instanceof HttpClientResponseException responseException) {
            return RETRYABLE_STATUS_CODES.contains(responseException.getStatus().getCode());
        }

        if (failure instanceof HttpClientException) {
            return true;
        }

        return hasCause(failure, IOException.class)
                || hasCause(failure, TimeoutException.class)
                || hasCause(failure, InterruptedException.class);
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void sleepBeforeRetry(SourceConfig sourceConfig, int attempt) {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "HTTP fetch retry interrupted for source " + sourceConfig.getName() +
                            " after attempt " + attempt,
                    e
            );
        }
    }

    private String conciseError(Exception failure) {
        if (failure instanceof HttpClientResponseException responseException) {
            return "HTTP " + responseException.getStatus().getCode() + " " +
                    responseException.getStatus().getReason();
        }

        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }

        return message;
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
