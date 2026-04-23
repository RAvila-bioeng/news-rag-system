package com.ragnews.ingestion.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class IngestionService {

    public Map<String, String> runIngestion() {
        return Map.of(
                "status", "ok",
                "message", "Ingestion triggered successfully"
        );
    }
}