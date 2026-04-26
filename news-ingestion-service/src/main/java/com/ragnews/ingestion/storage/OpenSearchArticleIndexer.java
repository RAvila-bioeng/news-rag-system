package com.ragnews.ingestion.storage;

import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.NormalizedArticle;
import io.micronaut.context.annotation.Value;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class OpenSearchArticleIndexer implements ArticleIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchArticleIndexer.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI documentUri;

    public OpenSearchArticleIndexer(
            ObjectMapper objectMapper,
            @Value("${opensearch.url:`http://localhost:9200`}") String openSearchUrl,
            @Value("${opensearch.index:`news_article`}") String indexName
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.documentUri = URI.create(openSearchUrl + "/" + indexName + "/_doc");
    }

    @Override
    public int indexArticles(List<ProcessedArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }

        int indexedCount = 0;
        for (ProcessedArticle article : articles) {
            if (indexArticle(article)) {
                indexedCount++;
            }
        }

        return indexedCount;
    }

    private boolean indexArticle(ProcessedArticle processedArticle) {
        try {
            String documentJson = objectMapper.writeValueAsString(toDocument(processedArticle));
            HttpRequest request = HttpRequest.newBuilder(documentUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(documentJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }

            LOG.error(
                    "OpenSearch indexing failed with status {}: {}",
                    response.statusCode(),
                    response.body()
            );
            return false;
        } catch (IOException e) {
            LOG.error("Could not serialize or send article to OpenSearch", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("OpenSearch indexing request was interrupted", e);
            return false;
        } catch (RuntimeException e) {
            LOG.error("Unexpected error while indexing article in OpenSearch", e);
            return false;
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
