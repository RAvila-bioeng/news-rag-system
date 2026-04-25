package com.ragnews.ingestion.web;

import com.ragnews.ingestion.service.IngestionService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Post(value = "/run", consumes = MediaType.ALL)
    public Map<String, Object> runIngestion() {
        return ingestionService.runIngestion();
    }
}
