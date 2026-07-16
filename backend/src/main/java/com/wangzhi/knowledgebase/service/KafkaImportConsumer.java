package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaImportConsumer {

    private final ImportBatchLeaseService leaseService;
    private final ImportProcessor processor;
    private final String workerId = "kafka-" + ManagementFactory.getRuntimeMXBean().getName() + "-"
            + UUID.randomUUID().toString().substring(0, 8);

    public KafkaImportConsumer(ImportBatchLeaseService leaseService, ImportProcessor processor) {
        this.leaseService = leaseService;
        this.processor = processor;
    }

    @KafkaListener(topics = "${app.kafka.import-topic:myrag.import.requested.v1}",
            groupId = "${app.kafka.consumer-group:myrag-import-workers}",
            concurrency = "${app.kafka.concurrency:1}")
    public void consume(String batchId) {
        leaseService.claim(batchId, workerId).ifPresent(claimed -> {
            try {
                processor.process(claimed, workerId);
            } catch (Exception exception) {
                leaseService.releaseAfterFailure(claimed, workerId, exception);
                throw new IllegalStateException("Kafka 导入任务执行失败", exception);
            }
        });
    }
}
