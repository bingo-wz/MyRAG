package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaOutboxRelay {

    private final OutboxClaimService claimService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final int leaseSeconds;
    private final int retentionDays;

    public KafkaOutboxRelay(OutboxClaimService claimService,
                            KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${app.kafka.outbox-batch-size:20}") int batchSize,
                            @Value("${app.kafka.outbox-lease-seconds:30}") int leaseSeconds,
                            @Value("${app.kafka.outbox-retention-days:7}") int retentionDays) {
        this.claimService = claimService;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = Math.max(1, batchSize);
        this.leaseSeconds = Math.max(10, leaseSeconds);
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-ms:500}")
    public void publish() {
        claimService.recoverExpired(batchSize);
        List<OutboxClaimService.ClaimedEvent> events = claimService.claim(batchSize, leaseSeconds);
        for (OutboxClaimService.ClaimedEvent event : events) {
            try {
                kafkaTemplate.send(event.topic(), event.eventKey(), event.payload()).get(10, TimeUnit.SECONDS);
                claimService.markPublished(event.id());
            } catch (Exception exception) {
                claimService.markFailed(event.id(), exception);
            }
        }
    }

    @Scheduled(cron = "${app.kafka.outbox-cleanup-cron:0 20 3 * * *}")
    public void cleanup() {
        claimService.cleanupPublished(retentionDays);
    }
}
