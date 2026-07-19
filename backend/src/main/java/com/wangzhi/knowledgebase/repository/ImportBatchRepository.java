package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {
    List<ImportBatch> findTop20ByOrderByCreatedAtDesc();

    long countByStatusIn(Collection<ImportBatchStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from ImportBatch batch
            where (batch.status = :queued and (batch.nextAttemptAt is null or batch.nextAttemptAt <= :now))
               or (batch.status = :processing and batch.leaseUntil < :now)
            order by batch.createdAt asc
            """)
    List<ImportBatch> findClaimable(@Param("queued") ImportBatchStatus queued,
                                    @Param("processing") ImportBatchStatus processing,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from ImportBatch batch where batch.id = :id")
    Optional<ImportBatch> findLockedById(@Param("id") String id);
}
