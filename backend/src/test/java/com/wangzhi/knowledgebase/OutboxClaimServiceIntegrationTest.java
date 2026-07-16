package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.domain.OutboxEvent;
import com.wangzhi.knowledgebase.domain.OutboxStatus;
import com.wangzhi.knowledgebase.repository.OutboxEventRepository;
import com.wangzhi.knowledgebase.service.OutboxClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo-data=false",
        "app.import.worker-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:outbox_claim;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class OutboxClaimServiceIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxClaimService claimService;

    @Test
    void shouldClaimRecoverPublishAndCleanupEvents() {
        OutboxEvent event = event("outbox-test-1");
        repository.saveAndFlush(event);

        assertThat(claimService.claim(10, 10)).singleElement()
                .satisfies(claimed -> assertThat(claimed.payload()).isEqualTo("batch-1"));
        OutboxEvent processing = repository.findById(event.getId()).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        processing.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        repository.saveAndFlush(processing);
        claimService.recoverExpired(10);
        assertThat(repository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);

        var reclaimed = claimService.claim(10, 10).getFirst();
        claimService.markPublished(reclaimed.id());
        OutboxEvent published = repository.findById(event.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);

        published.setPublishedAt(LocalDateTime.now().minusDays(8));
        repository.saveAndFlush(published);
        assertThat(claimService.cleanupPublished(7)).isEqualTo(1);
        assertThat(repository.findById(event.getId())).isEmpty();
    }

    private OutboxEvent event(String id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTopic("myrag.import.requested.v1");
        event.setEventKey("batch-1");
        event.setEventType("IMPORT_BATCH_REQUESTED");
        event.setPayload("batch-1");
        event.setStatus(OutboxStatus.PENDING);
        event.setNextAttemptAt(LocalDateTime.now());
        return event;
    }
}
