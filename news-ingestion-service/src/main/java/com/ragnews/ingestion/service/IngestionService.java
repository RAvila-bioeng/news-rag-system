package com.ragnews.ingestion.service;

import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class IngestionService {

    public Map<String, String> runIngestion() {
        return Map.of(
                "status", "ok",
                "message", "Ingestion triggered successfully"
        );
    }
}