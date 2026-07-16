package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.OutboxEvent;
import com.wangzhi.knowledgebase.domain.OutboxStatus;
import com.wangzhi.knowledgebase.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<ClaimedEvent> claim(int batchSize, int leaseSeconds) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = repository.findPublishable(OutboxStatus.PENDING, now,
                PageRequest.of(0, Math.max(1, batchSize)));
        events.forEach(event -> {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setNextAttemptAt(now.plusSeconds(Math.max(10, leaseSeconds)));
        });
        repository.saveAll(events);
        return events.stream().map(event -> new ClaimedEvent(event.getId(), event.getTopic(),
                event.getEventKey(), event.getPayload())).toList();
    }

    @Transactional
    public void recoverExpired(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> expired = repository.findPublishable(OutboxStatus.PROCESSING, now,
                PageRequest.of(0, Math.max(1, batchSize)));
        expired.forEach(event -> {
            event.setStatus(OutboxStatus.PENDING);
            event.setNextAttemptAt(now);
            event.setLastError("Outbox 投递租约超时，已重新入队");
        });
        repository.saveAll(expired);
    }

    @Transactional
    public void markPublished(String eventId) {
        repository.findLockedById(eventId).filter(event -> event.getStatus() == OutboxStatus.PROCESSING)
                .ifPresent(event -> {
                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(LocalDateTime.now());
                    event.setNextAttemptAt(null);
                    event.setLastError(null);
                    repository.save(event);
                });
    }

    @Transactional
    public void markFailed(String eventId, Throwable failure) {
        repository.findLockedById(eventId).filter(event -> event.getStatus() == OutboxStatus.PROCESSING)
                .ifPresent(event -> {
                    event.setAttempts(event.getAttempts() + 1);
                    event.setStatus(OutboxStatus.PENDING);
                    event.setNextAttemptAt(LocalDateTime.now().plusSeconds(
                            Math.min(300, 1L << Math.min(8, event.getAttempts()))));
                    event.setLastError(rootMessage(failure));
                    repository.save(event);
                });
    }

    @Transactional
    public int cleanupPublished(int retentionDays) {
        return repository.deletePublishedBefore(OutboxStatus.PUBLISHED,
                LocalDateTime.now().minusDays(Math.max(1, retentionDays)));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        return message.length() > 780 ? message.substring(0, 780) : message;
    }

    public record ClaimedEvent(String id, String topic, String eventKey, String payload) {}
}
