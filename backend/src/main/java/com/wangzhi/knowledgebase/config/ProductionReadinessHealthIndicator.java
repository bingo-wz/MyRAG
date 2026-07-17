package com.wangzhi.knowledgebase.config;

import com.wangzhi.knowledgebase.service.EmbeddingService;
import com.wangzhi.knowledgebase.service.ChatService;
import com.wangzhi.knowledgebase.security.FileSecurityScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("productionReadiness")
@ConditionalOnProperty(name = "app.readiness.enabled", havingValue = "true")
public class ProductionReadinessHealthIndicator implements HealthIndicator {

    private final EmbeddingService embeddingService;
    private final ChatService chatService;
    private final FileSecurityScanner fileSecurityScanner;
    private final String storageProvider;
    private final String vectorStore;
    private final boolean kafkaEnabled;
    private final boolean securityEnabled;

    public ProductionReadinessHealthIndicator(EmbeddingService embeddingService,
                                              ChatService chatService,
                                              FileSecurityScanner fileSecurityScanner,
                                              @Value("${app.storage.provider}") String storageProvider,
                                              @Value("${app.vector.store}") String vectorStore,
                                              @Value("${app.kafka.enabled:false}") boolean kafkaEnabled,
                                              @Value("${app.security.enabled:false}") boolean securityEnabled) {
        this.embeddingService = embeddingService;
        this.chatService = chatService;
        this.fileSecurityScanner = fileSecurityScanner;
        this.storageProvider = storageProvider;
        this.vectorStore = vectorStore;
        this.kafkaEnabled = kafkaEnabled;
        this.securityEnabled = securityEnabled;
    }

    @Override
    public Health health() {
        boolean fileScannerAvailable = fileSecurityScanner.available();
        boolean ready = embeddingService.semantic() && chatService.generative()
                && fileScannerAvailable && securityEnabled;
        Health.Builder health = ready ? Health.up() : Health.outOfService();
        return health.withDetail("embeddingModel", embeddingService.modelName())
                .withDetail("embeddingDimensions", embeddingService.dimensions())
                .withDetail("semanticEmbedding", embeddingService.semantic())
                .withDetail("chatModel", chatService.modelName())
                .withDetail("generativeChat", chatService.generative())
                .withDetail("fileScanner", fileSecurityScanner.provider())
                .withDetail("fileScanningActive", fileSecurityScanner.active())
                .withDetail("fileScannerAvailable", fileScannerAvailable)
                .withDetail("jwtSecurity", securityEnabled)
                .withDetail("objectStorage", storageProvider)
                .withDetail("vectorStore", vectorStore)
                .withDetail("kafka", kafkaEnabled)
                .build();
    }
}
