package com.ragnews.ingestion.web;

import com.ragnews.ingestion.service.IngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingestion/run")
    public Map<String, String> runIngestion() {
        return ingestionService.runIngestion();
    }
}