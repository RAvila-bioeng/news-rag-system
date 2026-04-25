package com.ragnews.ingestion.config;

import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                source.setName((String) rawSource.get("name"));
                source.setUrl((String) rawSource.get("url"));
                source.setSchedule((String) rawSource.get("schedule"));
                source.setParams((Map<String, String>) rawSource.get("params"));
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
}