package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.import.worker-enabled", havingValue = "true", matchIfMissing = true)
public class DurableImportWorker {

    private final ImportBatchLeaseService leaseService;
    private final ImportProcessor processor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-"
            + UUID.randomUUID().toString().substring(0, 8);

    public DurableImportWorker(ImportBatchLeaseService leaseService, ImportProcessor processor) {
        this.leaseService = leaseService;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.import.poll-interval-ms:500}",
            initialDelayString = "${app.import.recovery-delay-ms:500}")
    public void poll() {
        leaseService.claim(workerId).ifPresent(batchId -> {
            try {
                processor.process(batchId, workerId);
            } catch (Exception exception) {
                leaseService.releaseAfterFailure(batchId, workerId, exception);
            }
        });
    }
}
