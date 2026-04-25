package com.ragnews.ingestion.metrics;

import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MetricsWriter {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsWriter.class);

    private final ObjectMapper objectMapper;
    private final Path metricsFilePath;

    public MetricsWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.metricsFilePath = resolveMetricsFilePath();
    }

    public void write(IngestionRunMetrics runMetrics) {
        try {
            LOG.info("Resolved ingestion metrics path: {}", metricsFilePath);
            Files.createDirectories(metricsFilePath.getParent());

            MetricsFile currentMetrics = readCurrentMetrics();
            List<IngestionRunMetrics> runs = new ArrayList<>(currentMetrics.runs());
            runs.add(runMetrics);

            MetricsFile updatedMetrics = new MetricsFile(runMetrics.runAt(), runs);
            String metricsJson = objectMapper.writeValueAsString(updatedMetrics);
            Files.writeString(metricsFilePath, prettyPrint(metricsJson), StandardCharsets.UTF_8);

            LOG.info("Metrics file written to {}", metricsFilePath);
            LOG.info("Metrics file now stores {} ingestion runs", runs.size());
        } catch (IOException e) {
            LOG.error("Could not write ingestion metrics to {}", metricsFilePath, e);
        }
    }

    private MetricsFile readCurrentMetrics() {
        if (!Files.exists(metricsFilePath)) {
            return new MetricsFile(null, List.of());
        }

        try {
            if (Files.size(metricsFilePath) == 0) {
                return new MetricsFile(null, List.of());
            }

            String metricsJson = Files.readString(metricsFilePath, StandardCharsets.UTF_8);
            MetricsFile metricsFile = objectMapper.readValue(metricsJson, MetricsFile.class);
            if (metricsFile.runs() == null) {
                return new MetricsFile(metricsFile.lastRunAt(), List.of());
            }

            return metricsFile;
        } catch (IOException e) {
            LOG.warn(
                    "Could not read existing metrics file at {}. Starting a new metrics file.",
                    metricsFilePath,
                    e
            );
            return new MetricsFile(null, List.of());
        }
    }

    private Path resolveMetricsFilePath() {
        Path repoRoot = findRepoRoot();

        return repoRoot
                .resolve("data")
                .resolve("metrics")
                .resolve("ingestion-metrics.json")
                .toAbsolutePath()
                .normalize();
    }

    private Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        while (current != null) {
            boolean hasRootConfig = Files.exists(current.resolve("config").resolve("sources.yaml"));
            boolean hasIngestionService = Files.isDirectory(current.resolve("news-ingestion-service"));

            if (hasRootConfig && hasIngestionService) {
                return current;
            }

            current = current.getParent();
        }

        throw new IllegalStateException(
                "Could not find repository root from " + Path.of(System.getProperty("user.dir")).toAbsolutePath()
        );
    }

    private String prettyPrint(String compactJson) {
        StringBuilder prettyJson = new StringBuilder();
        int indentLevel = 0;
        boolean insideString = false;
        boolean escaping = false;

        for (int i = 0; i < compactJson.length(); i++) {
            char current = compactJson.charAt(i);

            if (escaping) {
                prettyJson.append(current);
                escaping = false;
                continue;
            }

            if (current == '\\') {
                prettyJson.append(current);
                escaping = insideString;
                continue;
            }

            if (current == '"') {
                insideString = !insideString;
                prettyJson.append(current);
                continue;
            }

            if (insideString) {
                prettyJson.append(current);
                continue;
            }

            if (current == '{' || current == '[') {
                prettyJson.append(current);
                prettyJson.append(System.lineSeparator());
                indentLevel++;
                appendIndent(prettyJson, indentLevel);
            } else if (current == '}' || current == ']') {
                prettyJson.append(System.lineSeparator());
                indentLevel--;
                appendIndent(prettyJson, indentLevel);
                prettyJson.append(current);
            } else if (current == ',') {
                prettyJson.append(current);
                prettyJson.append(System.lineSeparator());
                appendIndent(prettyJson, indentLevel);
            } else if (current == ':') {
                prettyJson.append(": ");
            } else {
                prettyJson.append(current);
            }
        }

        prettyJson.append(System.lineSeparator());
        return prettyJson.toString();
    }

    private void appendIndent(StringBuilder prettyJson, int indentLevel) {
        prettyJson.append("  ".repeat(Math.max(0, indentLevel)));
    }
}
