package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.config.ProductionReadinessHealthIndicator;
import com.wangzhi.knowledgebase.service.ChatService;
import com.wangzhi.knowledgebase.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionReadinessHealthIndicatorTest {

    @Test
    void reportsUpWhenProductionDependenciesAreConfigured() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        ChatService chat = mock(ChatService.class);
        when(embedding.semantic()).thenReturn(true);
        when(embedding.modelName()).thenReturn("test-embedding");
        when(embedding.dimensions()).thenReturn(1024);
        when(chat.generative()).thenReturn(true);
        when(chat.modelName()).thenReturn("test-chat");
        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(
                embedding, chat, "minio", "milvus", true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails())
                .containsEntry("semanticEmbedding", true)
                .containsEntry("generativeChat", true)
                .doesNotContainKeys("fileScanner", "fileScanningActive", "fileScannerAvailable");
    }

    @Test
    void reportsOutOfServiceWhenSemanticDependenciesAreUnavailable() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        ChatService chat = mock(ChatService.class);
        when(embedding.semantic()).thenReturn(false);
        when(embedding.modelName()).thenReturn("test-embedding");
        when(embedding.dimensions()).thenReturn(1024);
        when(chat.generative()).thenReturn(true);
        when(chat.modelName()).thenReturn("test-chat");
        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(
                embedding, chat, "minio", "milvus", true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }
}
