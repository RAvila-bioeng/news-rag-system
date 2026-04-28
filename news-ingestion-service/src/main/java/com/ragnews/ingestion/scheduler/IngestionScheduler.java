package com.ragnews.ingestion.scheduler;

import com.ragnews.ingestion.service.IngestionService;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IngestionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionService ingestionService;
    private final boolean enabled;

    public IngestionScheduler(
            IngestionService ingestionService,
            @Value("${ingestion.scheduler.enabled:true}") boolean enabled
    ) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Scheduled(cron = "${ingestion.scheduler.cron:0 0 8 * * ?}")
    public void runScheduledIngestion() {
        if (!enabled) {
            LOG.debug("Scheduled ingestion is disabled");
            return;
        }

        try {
            LOG.info("Scheduled ingestion started");
            ingestionService.runIngestion();
            LOG.info("Scheduled ingestion completed");
        } catch (Exception e) {
            LOG.error("Scheduled ingestion failed", e);
        }
    }
}
