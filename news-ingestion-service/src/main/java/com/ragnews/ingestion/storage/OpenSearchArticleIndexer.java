package com.ragnews.ingestion.storage;

import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.NormalizedArticle;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class OpenSearchArticleIndexer implements ArticleIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchArticleIndexer.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String documentUrl;

    public OpenSearchArticleIndexer(
            ObjectMapper objectMapper,
            @Value("${opensearch.url:`http://localhost:9200`}") String openSearchUrl,
            @Value("${opensearch.index:`news_article`}") String indexName
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.documentUrl = openSearchUrl + "/" + indexName + "/_doc";
    }

    @Override
    public IndexingSummary indexArticles(List<ProcessedArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return new IndexingSummary(0, 0, 0);
        }

        int indexedCount = 0;
        int createdCount = 0;
        int updatedCount = 0;

        for (ProcessedArticle article : articles) {
            String operationResult = indexArticle(article);
            if (operationResult != null) {
                indexedCount++;

                if ("created".equals(operationResult)) {
                    createdCount++;
                } else if ("updated".equals(operationResult)) {
                    updatedCount++;
                } else {
                    LOG.warn("OpenSearch returned unexpected successful indexing result: {}", operationResult);
                }
            }
        }

        return new IndexingSummary(indexedCount, createdCount, updatedCount);
    }

    private String indexArticle(ProcessedArticle processedArticle) {
        try {
            String documentId = generateDocumentId(processedArticle);
            NormalizedArticle article = processedArticle.article();
            String documentJson = objectMapper.writeValueAsString(toDocument(processedArticle));
            HttpRequest request = HttpRequest.newBuilder(URI.create(documentUrl + "/" + documentId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(documentJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String operationResult = parseOperationResult(response.body());
                LOG.debug(
                        "Indexed article '{}' with document ID {} and result {}",
                        article.title(),
                        documentId,
                        operationResult
                );
                return operationResult;
            }

            LOG.error(
                    "OpenSearch indexing failed with status {}: {}",
                    response.statusCode(),
                    response.body()
            );
            return null;
        } catch (IOException e) {
            LOG.error("Could not serialize or send article to OpenSearch", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("OpenSearch indexing request was interrupted", e);
            return null;
        } catch (RuntimeException e) {
            LOG.error("Unexpected error while indexing article in OpenSearch", e);
            return null;
        }
    }

    private String parseOperationResult(String responseBody) throws IOException {
        Map<String, Object> response = objectMapper.readValue(
                responseBody,
                Argument.mapOf(String.class, Object.class)
        );
        Object result = response.get("result");
        return result == null ? "unknown" : result.toString();
    }

    private String generateDocumentId(ProcessedArticle processedArticle) {
        NormalizedArticle article = processedArticle.article();
        String idSource = article.url();

        if (idSource == null || idSource.isBlank()) {
            idSource = article.source() + "|" + article.title() + "|" + article.publishedAt();
        }

        return sha256Hex(idSource.trim());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private Map<String, Object> toDocument(ProcessedArticle processedArticle) {
        NormalizedArticle article = processedArticle.article();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", article.title());
        document.put("content", article.content());
        document.put("source", article.source());
        document.put("url", article.url());
        document.put("timestamp", article.publishedAt());
        document.put("sentiment", processedArticle.sentiment().name().toLowerCase());
        document.put("embedding", processedArticle.embedding());
        return document;
    }
}
