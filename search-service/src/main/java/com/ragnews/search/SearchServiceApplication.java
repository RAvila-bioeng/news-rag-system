package com.ragnews.search;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import io.micronaut.runtime.Micronaut;

import java.nio.file.Files;
import java.nio.file.Path;

public class SearchServiceApplication {

    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();
        Micronaut.run(SearchServiceApplication.class, args);
    }

    private static void loadDotenvIntoSystemProperties() {
        Dotenv dotenv = Dotenv.configure()
                .directory(findRepoRoot().toString())
                .ignoreIfMissing()
                .load();

        for (DotenvEntry entry : dotenv.entries()) {
            if (entry.getValue() != null
                    && !entry.getValue().isBlank()
                    && System.getenv(entry.getKey()) == null
                    && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve(".env"))
                    || Files.exists(current.resolve("config").resolve("sources.yaml"))) {
                return current;
            }

            current = current.getParent();
        }

        return Path.of("").toAbsolutePath();
    }
}
