package com.ragnews.ingestion.config;

import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SourceConfigurationLoader {

    private static final String CONFIG_RELATIVE_PATH = "config/sources.yaml";

    public List<SourceConfig> loadSources() {
        Path configPath = resolveConfigPath();
        Yaml yaml = new Yaml();

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Map<String, Object> root = yaml.load(inputStream);

            if (root == null || !root.containsKey("sources")) {
                return List.of();
            }

            List<Map<String, Object>> rawSources =
                    (List<Map<String, Object>>) root.get("sources");

            List<SourceConfig> sources = new ArrayList<>();

            for (Map<String, Object> rawSource : rawSources) {
                SourceConfig source = new SourceConfig();
                source.setName(asString(rawSource.get("name")));
                source.setEnabled(asBoolean(rawSource.get("enabled"), true));
                source.setType(defaultIfBlank(asString(rawSource.get("type")), "generic-json"));
                source.setUrl(asString(rawSource.get("url")));
                source.setMethod(defaultIfBlank(asString(rawSource.get("method")), "GET"));
                source.setHeaders(asStringMap(rawSource.get("headers")));
                source.setParams(asStringMap(rawSource.get("params")));
                source.setSchedule(asString(rawSource.get("schedule")));
                source.setResponseMapping(asResponseMapping(rawSource.get("responseMapping")));
                sources.add(source);
            }

            return sources;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not load source configuration from " + configPath.toAbsolutePath(),
                    e
            );
        }
    }

    private Path resolveConfigPath() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            Path candidate = current.resolve(CONFIG_RELATIVE_PATH);

            if (Files.exists(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        throw new IllegalStateException(
                "Could not find source configuration by searching upwards for " + CONFIG_RELATIVE_PATH +
                        " from " + Path.of("").toAbsolutePath()
        );
    }

    private ResponseMappingConfig asResponseMapping(Object value) {
        if (!(value instanceof Map<?, ?> rawMapping)) {
            return null;
        }

        ResponseMappingConfig responseMapping = new ResponseMappingConfig();
        responseMapping.setArticlesPath(asString(rawMapping.get("articlesPath")));
        responseMapping.setTitle(asString(rawMapping.get("title")));
        responseMapping.setContent(asString(rawMapping.get("content")));
        responseMapping.setUrl(asString(rawMapping.get("url")));
        responseMapping.setSource(asString(rawMapping.get("source")));
        responseMapping.setTimestamp(asString(rawMapping.get("timestamp")));
        return responseMapping;
    }

    private Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = asString(entry.getKey());
            String mapValue = asString(entry.getValue());

            if (key != null) {
                result.put(key, mapValue);
            }
        }

        return result;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String stringValue = asString(value);
        if (stringValue == null || stringValue.isBlank()) {
            return defaultValue;
        }

        return Boolean.parseBoolean(stringValue);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }
}
