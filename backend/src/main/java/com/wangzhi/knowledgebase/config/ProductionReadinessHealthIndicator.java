package com.wangzhi.knowledgebase.config;

import com.wangzhi.knowledgebase.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("productionReadiness")
@ConditionalOnProperty(name = "app.readiness.enabled", havingValue = "true")
public class ProductionReadinessHealthIndicator implements HealthIndicator {

    private final EmbeddingService embeddingService;
    private final String storageProvider;
    private final String vectorStore;
    private final boolean kafkaEnabled;

    public ProductionReadinessHealthIndicator(EmbeddingService embeddingService,
                                              @Value("${app.storage.provider}") String storageProvider,
                                              @Value("${app.vector.store}") String vectorStore,
                                              @Value("${app.kafka.enabled:false}") boolean kafkaEnabled) {
        this.embeddingService = embeddingService;
        this.storageProvider = storageProvider;
        this.vectorStore = vectorStore;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Override
    public Health health() {
        Health.Builder health = embeddingService.semantic() ? Health.up() : Health.outOfService();
        return health.withDetail("embeddingModel", embeddingService.modelName())
                .withDetail("embeddingDimensions", embeddingService.dimensions())
                .withDetail("semanticEmbedding", embeddingService.semantic())
                .withDetail("objectStorage", storageProvider)
                .withDetail("vectorStore", vectorStore)
                .withDetail("kafka", kafkaEnabled)
                .build();
    }
}
