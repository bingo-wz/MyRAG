package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ImportFileTaskRepository extends JpaRepository<ImportFileTask, Long> {
    List<ImportFileTask> findByBatchIdOrderByIdAsc(String batchId);
    List<ImportFileTask> findByBatchIdAndStatus(String batchId, ImportFileStatus status);
    List<ImportFileTask> findByBatchIdAndStatusOrderByIdAsc(String batchId, ImportFileStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ImportFileTask task
            set task.status = :claimed, task.errorMessage = null
            where task.id = :id and task.status = :queued
            """)
    int claim(@Param("id") Long id,
              @Param("queued") ImportFileStatus queued,
              @Param("claimed") ImportFileStatus claimed);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ImportFileTask task
            set task.status = :queued,
                task.errorMessage = :reason,
                task.retryCount = task.retryCount + 1
            where task.batchId = :batchId and task.status in :interrupted
            """)
    int requeueInterrupted(@Param("batchId") String batchId,
                           @Param("interrupted") Collection<ImportFileStatus> interrupted,
                           @Param("queued") ImportFileStatus queued,
                           @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ImportFileTask task
            set task.status = :failed, task.errorMessage = :reason
            where task.batchId = :batchId and task.status in :recoverable
            """)
    int failRecoverable(@Param("batchId") String batchId,
                        @Param("recoverable") Collection<ImportFileStatus> recoverable,
                        @Param("failed") ImportFileStatus failed,
                        @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ImportFileTask task set task.documentId = null where task.documentId = :documentId")
    int clearDocumentReference(@Param("documentId") Long documentId);
}
