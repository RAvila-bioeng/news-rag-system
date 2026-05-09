package com.ragnews.ingestion.fetcher;

import com.ragnews.ingestion.config.SourceConfig;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenericHttpNewsFetcherTest {

    @Test
    void retriesTransientFailuresAndReturnsSuccessfulResponse() {
        RetryingFetcher fetcher = new RetryingFetcher(2, 0, 2, null);

        String response = fetcher.fetch(source("NewsAPI"));

        assertEquals("{\"articles\":[]}", response);
        assertEquals(3, fetcher.attempts);
    }

    @Test
    void doesNotRetryNonRetryableClientErrors() {
        HttpClientResponseException badRequest = new HttpClientResponseException(
                "bad request",
                HttpResponse.status(HttpStatus.BAD_REQUEST)
        );
        RetryingFetcher fetcher = new RetryingFetcher(3, 0, Integer.MAX_VALUE, badRequest);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fetcher.fetch(source("NewsAPI"))
        );

        assertEquals(1, fetcher.attempts);
        assertEquals("HTTP fetch failed for source NewsAPI: HTTP 400 Bad Request", exception.getMessage());
    }

    private SourceConfig source(String name) {
        SourceConfig source = new SourceConfig();
        source.setName(name);
        source.setUrl("https://example.com/news");
        return source;
    }

    private static final class RetryingFetcher extends GenericHttpNewsFetcher {
        private final int failuresBeforeSuccess;
        private final RuntimeException failure;
        private int attempts;

        private RetryingFetcher(
                int maxRetries,
                long retryDelayMs,
                int failuresBeforeSuccess,
                RuntimeException failure
        ) {
            super(null, null, maxRetries, retryDelayMs);
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.failure = failure;
        }

        @Override
        protected String retrieve(MutableHttpRequest<?> request) {
            attempts++;
            if (attempts <= failuresBeforeSuccess) {
                if (failure != null) {
                    throw failure;
                }

                throw new HttpClientException("temporary network failure", new IOException("connection reset"));
            }

            return "{\"articles\":[]}";
        }
    }
}
