package com.ragnews.ingestion.service;

import com.ragnews.ingestion.config.SourceConfig;
import com.ragnews.ingestion.config.SourceConfigurationLoader;
import com.ragnews.ingestion.enrichment.ArticleEnricher;
import com.ragnews.ingestion.fetcher.GenericHttpNewsFetcher;
import com.ragnews.ingestion.metrics.IngestionRunMetrics;
import com.ragnews.ingestion.metrics.MetricsWriter;
import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.GenericJsonArticleParser;
import com.ragnews.ingestion.parser.NormalizedArticle;
import com.ragnews.ingestion.sentiment.Sentiment;
import com.ragnews.ingestion.storage.ArticleIndexer;
import com.ragnews.ingestion.storage.IndexingSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestionServiceTest {

    @Test
    void continuesWhenOneSourceFailsAndAnotherSucceeds() {
        IngestionService service = service(List.of(source("broken"), source("working")), List.of("broken"));

        Map<String, Object> result = service.runIngestion();

        assertEquals("partial", result.get("status"));
        assertEquals(2, result.get("sourceCount"));
        assertEquals(1, result.get("fetchedCount"));
        assertEquals(1, result.get("normalizedCount"));
        assertEquals(1, result.get("processedCount"));
        assertEquals(1, result.get("embeddedCount"));
        assertEquals(1, result.get("indexedCount"));

        List<?> sources = (List<?>) result.get("sources");
        Map<?, ?> failedSource = (Map<?, ?>) sources.get(0);
        Map<?, ?> successfulSource = (Map<?, ?>) sources.get(1);

        assertEquals("failed", failedSource.get("status"));
        assertEquals(1, failedSource.get("failedRequests"));
        assertEquals(0, failedSource.get("indexedCount"));
        assertEquals("ok", successfulSource.get("status"));
    }

    @Test
    void returnsFailedWhenAllSourcesFail() {
        IngestionService service = service(List.of(source("broken-1"), source("broken-2")), List.of("broken-1", "broken-2"));

        Map<String, Object> result = service.runIngestion();

        assertEquals("failed", result.get("status"));
        assertEquals(2, result.get("sourceCount"));
        assertEquals(0, result.get("fetchedCount"));
        assertEquals(0, result.get("indexedCount"));
    }

    private IngestionService service(List<SourceConfig> sources, List<String> failingSources) {
        return new IngestionService(
                new FakeSourceConfigurationLoader(sources),
                new FakeFetcher(failingSources),
                new FakeParser(),
                new FakeEnricher(),
                new FakeIndexer(),
                new FakeMetricsWriter()
        );
    }

    private SourceConfig source(String name) {
        SourceConfig source = new SourceConfig();
        source.setName(name);
        source.setEnabled(true);
        source.setType("generic-json");
        return source;
    }

    private static final class FakeSourceConfigurationLoader extends SourceConfigurationLoader {
        private final List<SourceConfig> sources;

        private FakeSourceConfigurationLoader(List<SourceConfig> sources) {
            this.sources = sources;
        }

        @Override
        public List<SourceConfig> loadSources() {
            return sources;
        }
    }

    private static final class FakeFetcher extends GenericHttpNewsFetcher {
        private final List<String> failingSources;

        private FakeFetcher(List<String> failingSources) {
            super(null, null, 0, 0);
            this.failingSources = failingSources;
        }

        @Override
        public String fetch(SourceConfig sourceConfig) {
            if (failingSources.contains(sourceConfig.getName())) {
                throw new IllegalStateException("simulated source failure");
            }

            return "{}";
        }
    }

    private static final class FakeParser extends GenericJsonArticleParser {
        private FakeParser() {
            super(null);
        }

        @Override
        public List<NormalizedArticle> parse(String rawJson, SourceConfig sourceConfig) {
            return List.of(new NormalizedArticle(
                    sourceConfig.getName() + "-1",
                    "Title",
                    "Useful news content",
                    sourceConfig.getName(),
                    null,
                    "https://example.com/" + sourceConfig.getName(),
                    Instant.parse("2026-05-09T00:00:00Z")
            ));
        }
    }

    private static final class FakeEnricher extends ArticleEnricher {
        private FakeEnricher() {
            super(null, null);
        }

        @Override
        public List<ProcessedArticle> enrich(List<NormalizedArticle> articles) {
            return articles.stream()
                    .map(article -> new ProcessedArticle(article, Sentiment.NEUTRAL, List.of(0.1, 0.2, 0.3)))
                    .toList();
        }
    }

    private static final class FakeIndexer implements ArticleIndexer {
        @Override
        public IndexingSummary indexArticles(List<ProcessedArticle> articles) {
            return new IndexingSummary(articles.size(), articles.size(), 0);
        }
    }

    private static final class FakeMetricsWriter extends MetricsWriter {
        private final List<IngestionRunMetrics> writtenMetrics = new ArrayList<>();

        private FakeMetricsWriter() {
            super(null);
        }

        @Override
        public void write(IngestionRunMetrics runMetrics) {
            writtenMetrics.add(runMetrics);
        }
    }
}
