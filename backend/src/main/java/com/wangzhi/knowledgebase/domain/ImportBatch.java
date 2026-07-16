package com.wangzhi.knowledgebase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @Column(length = 40)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportBatchStatus status;

    @Column(nullable = false, length = 80)
    private String domain;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(length = 300)
    private String tags;

    @Column(nullable = false)
    private int totalFiles;

    @Column(nullable = false)
    private int processedFiles;

    @Column(nullable = false)
    private int succeededFiles;

    @Column(nullable = false)
    private int failedFiles;

    @Column(length = 100)
    private String workerId;

    private LocalDateTime leaseUntil;

    private LocalDateTime nextAttemptAt;

    @Column(nullable = false)
    private int attemptCount;

    @Column(length = 800)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ImportBatchStatus getStatus() { return status; }
    public void setStatus(ImportBatchStatus status) { this.status = status; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    public int getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(int processedFiles) { this.processedFiles = processedFiles; }
    public int getSucceededFiles() { return succeededFiles; }
    public void setSucceededFiles(int succeededFiles) { this.succeededFiles = succeededFiles; }
    public int getFailedFiles() { return failedFiles; }
    public void setFailedFiles(int failedFiles) { this.failedFiles = failedFiles; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
