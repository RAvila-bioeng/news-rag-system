package com.ragnews.ingestion.parser;

import com.ragnews.ingestion.model.NewsApiResponse;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Singleton
public class NewsApiArticleParser implements ArticleParser<NewsApiResponse> {

    private static final String UNKNOWN_SOURCE = "Unknown";

    @Override
    public List<NormalizedArticle> parse(NewsApiResponse rawResponse) {
        return parse(rawResponse, UNKNOWN_SOURCE);
    }

    public List<NormalizedArticle> parse(NewsApiResponse rawResponse, String fallbackSourceName) {
        if (rawResponse == null || rawResponse.getArticles() == null) {
            return List.of();
        }

        return rawResponse.getArticles().stream()
                .filter(article -> hasText(article.getTitle()))
                .filter(article -> hasText(article.getUrl()))
                .map(article -> toNormalizedArticle(article, fallbackSourceName))
                .toList();
    }

    private NormalizedArticle toNormalizedArticle(
            NewsApiResponse.Article article,
            String fallbackSourceName
    ) {
        String sourceName = sourceName(article, fallbackSourceName);

        return new NormalizedArticle(
                article.getUrl(),
                article.getTitle(),
                articleContent(article),
                sourceName,
                article.getAuthor(),
                article.getUrl(),
                publishedAt(article.getPublishedAt())
        );
    }

    private String articleContent(NewsApiResponse.Article article) {
        if (hasText(article.getContent())) {
            return article.getContent();
        }

        if (hasText(article.getDescription())) {
            return article.getDescription();
        }

        return "";
    }

    private String sourceName(NewsApiResponse.Article article, String fallbackSourceName) {
        if (article.getSource() != null && hasText(article.getSource().getName())) {
            return article.getSource().getName();
        }

        if (hasText(fallbackSourceName)) {
            return fallbackSourceName;
        }

        return UNKNOWN_SOURCE;
    }

    private Instant publishedAt(String value) {
        if (!hasText(value)) {
            return Instant.now();
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
