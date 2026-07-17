package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.config.ProductionReadinessHealthIndicator;
import com.wangzhi.knowledgebase.security.FileSecurityScanner;
import com.wangzhi.knowledgebase.service.ChatService;
import com.wangzhi.knowledgebase.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.io.InputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionReadinessHealthIndicatorTest {

    @Test
    void reportsOutOfServiceWhenScannerIsUnavailable() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        ChatService chat = mock(ChatService.class);
        when(embedding.semantic()).thenReturn(true);
        when(embedding.modelName()).thenReturn("test-embedding");
        when(chat.generative()).thenReturn(true);
        when(chat.modelName()).thenReturn("test-chat");
        FileSecurityScanner scanner = new FileSecurityScanner() {
            @Override
            public void scan(InputStream input, String originalName) {
            }

            @Override
            public String provider() {
                return "clamav";
            }

            @Override
            public boolean available() {
                return false;
            }
        };
        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(
                embedding, chat, scanner, "minio", "milvus", true, true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(indicator.health().getDetails()).containsEntry("fileScannerAvailable", false);
    }
}
