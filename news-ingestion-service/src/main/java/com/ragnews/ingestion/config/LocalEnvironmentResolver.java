package com.ragnews.ingestion.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class LocalEnvironmentResolver {

    public String resolveRequired(String name) {
        String value = resolve(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable or .env value: " + name);
        }

        return value;
    }

    public String resolve(String name) {
        String envValue = System.getenv(name);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(findRepoRoot().toString())
                .ignoreIfMissing()
                .load();

        return dotenv.get(name);
    }

    private Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve(".env")) ||
                    Files.exists(current.resolve("config").resolve("sources.yaml"))) {
                return current;
            }

            current = current.getParent();
        }

        return Path.of("").toAbsolutePath();
    }
}
