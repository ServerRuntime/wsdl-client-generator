package com.example.wsdlgenerator.config;

import com.example.wsdlgenerator.service.GenerationOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CleanupScheduler {

    private final GenerationOrchestrator orchestrator;

    @Value("${app.job-ttl-minutes:60}")
    private int ttlMinutes;

    public CleanupScheduler(GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "PT30M")
    public void cleanup() {
        orchestrator.cleanupExpiredJobs(ttlMinutes);
    }
}
