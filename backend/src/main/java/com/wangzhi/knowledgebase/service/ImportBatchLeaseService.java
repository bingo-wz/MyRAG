package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ImportBatchLeaseService {

    private final ImportBatchRepository repository;
    private final Duration leaseDuration;
    private final int maxAttempts;

    public ImportBatchLeaseService(ImportBatchRepository repository,
                                   @Value("${app.import.lease-seconds:300}") long leaseSeconds,
                                   @Value("${app.import.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseSeconds));
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public Optional<String> claim(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findClaimable(ImportBatchStatus.QUEUED, ImportBatchStatus.PROCESSING,
                        now, PageRequest.of(0, 1)).stream().findFirst().map(batch -> {
            batch.setStatus(ImportBatchStatus.PROCESSING);
            batch.setWorkerId(workerId);
            batch.setLeaseUntil(now.plus(leaseDuration));
            batch.setNextAttemptAt(null);
            batch.setAttemptCount(batch.getAttemptCount() + 1);
            batch.setLastError(null);
            repository.save(batch);
            return batch.getId();
        });
    }

    @Transactional
    public Optional<String> claim(String batchId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findLockedById(batchId).filter(batch ->
                        (batch.getStatus() == ImportBatchStatus.QUEUED
                                && (batch.getNextAttemptAt() == null || !batch.getNextAttemptAt().isAfter(now)))
                                || (batch.getStatus() == ImportBatchStatus.PROCESSING
                                && batch.getLeaseUntil() != null && batch.getLeaseUntil().isBefore(now)))
                .map(batch -> {
                    batch.setStatus(ImportBatchStatus.PROCESSING);
                    batch.setWorkerId(workerId);
                    batch.setLeaseUntil(now.plus(leaseDuration));
                    batch.setNextAttemptAt(null);
                    batch.setAttemptCount(batch.getAttemptCount() + 1);
                    batch.setLastError(null);
                    repository.save(batch);
                    return batch.getId();
                });
    }

    @Transactional
    public void heartbeat(String batchId, String workerId) {
        repository.findById(batchId).filter(batch -> workerId.equals(batch.getWorkerId())).ifPresent(batch -> {
            batch.setLeaseUntil(LocalDateTime.now().plus(leaseDuration));
            repository.save(batch);
        });
    }

    @Transactional
    public void releaseAfterFailure(String batchId, String workerId, Throwable failure) {
        repository.findById(batchId).filter(batch -> workerId.equals(batch.getWorkerId())).ifPresent(batch -> {
            String message = rootMessage(failure);
            batch.setLastError(message);
            batch.setWorkerId(null);
            batch.setLeaseUntil(null);
            if (batch.getAttemptCount() >= maxAttempts) {
                batch.setStatus(ImportBatchStatus.FAILED);
            } else {
                long delaySeconds = Math.min(300, 5L << Math.min(6, batch.getAttemptCount() - 1));
                batch.setStatus(ImportBatchStatus.QUEUED);
                batch.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            }
            repository.save(batch);
        });
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        return message.length() > 780 ? message.substring(0, 780) : message;
    }
}
