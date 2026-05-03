package com.ragnews.ingestion.parser;

import com.ragnews.ingestion.config.ResponseMappingConfig;
import com.ragnews.ingestion.config.SourceConfig;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class GenericJsonArticleParser {

    private final ObjectMapper objectMapper;

    public GenericJsonArticleParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<NormalizedArticle> parse(String rawJson, SourceConfig sourceConfig) {
        ResponseMappingConfig mapping = requireResponseMapping(sourceConfig);
        Object root = readJson(rawJson, sourceConfig);
        Object rawArticles = resolvePath(root, mapping.getArticlesPath());

        if (!(rawArticles instanceof List<?> articles)) {
            throw new IllegalArgumentException(
                    "Configured articlesPath for source " + sourceConfig.getName() +
                            " did not resolve to a JSON array: " + mapping.getArticlesPath()
            );
        }

        return articles.stream()
                .map(article -> toNormalizedArticle(article, mapping, sourceConfig))
                .filter(Objects::nonNull)
                .toList();
    }

    private NormalizedArticle toNormalizedArticle(
            Object rawArticle,
            ResponseMappingConfig mapping,
            SourceConfig sourceConfig
    ) {
        String title = resolveString(rawArticle, mapping.getTitle());
        if (!hasText(title)) {
            return null;
        }

        String content = resolveString(rawArticle, mapping.getContent());
        if (!hasText(content)) {
            content = title;
        }

        String url = resolveString(rawArticle, mapping.getUrl());
        String articleSource = resolveString(rawArticle, mapping.getSource());
        if (!hasText(articleSource)) {
            articleSource = sourceConfig.getName();
        }

        Instant publishedAt = parseTimestamp(resolveString(rawArticle, mapping.getTimestamp()));

        return new NormalizedArticle(
                url,
                title,
                content,
                articleSource,
                null,
                url,
                publishedAt
        );
    }

    private ResponseMappingConfig requireResponseMapping(SourceConfig sourceConfig) {
        if (sourceConfig.getResponseMapping() == null) {
            throw new IllegalArgumentException(
                    "Missing responseMapping for source " + sourceConfig.getName()
            );
        }

        if (!hasText(sourceConfig.getResponseMapping().getArticlesPath())) {
            throw new IllegalArgumentException(
                    "Missing responseMapping.articlesPath for source " + sourceConfig.getName()
            );
        }

        return sourceConfig.getResponseMapping();
    }

    private Object readJson(String rawJson, SourceConfig sourceConfig) {
        try {
            return objectMapper.readValue(rawJson, Object.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Could not parse JSON response for source " + sourceConfig.getName(),
                    e
            );
        }
    }

    private String resolveString(Object root, String path) {
        Object value = resolvePath(root, path);
        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private Object resolvePath(Object root, String path) {
        if (!hasText(path)) {
            return null;
        }

        String normalizedPath = path.trim();
        if ("$".equals(normalizedPath)) {
            return root;
        }

        if (normalizedPath.startsWith("$.")) {
            normalizedPath = normalizedPath.substring(2);
        }

        Object current = root;
        for (String segment : normalizedPath.split("\\.")) {
            if (!hasText(segment)) {
                continue;
            }

            current = resolveSegment(current, segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private Object resolveSegment(Object current, String segment) {
        if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        }

        if (current instanceof List<?> list) {
            int index = parseIndex(segment);
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            }
        }

        return null;
    }

    private int parseIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Instant parseTimestamp(String value) {
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
