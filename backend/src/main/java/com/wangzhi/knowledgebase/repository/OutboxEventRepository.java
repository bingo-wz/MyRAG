package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.OutboxEvent;
import com.wangzhi.knowledgebase.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from OutboxEvent event
            where event.status = :status and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
            order by event.createdAt asc
            """)
    List<OutboxEvent> findPublishable(@Param("status") OutboxStatus status,
                                      @Param("now") LocalDateTime now,
                                      Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    java.util.Optional<OutboxEvent> findLockedById(@Param("id") String id);

    @Modifying
    @Query("delete from OutboxEvent event where event.status = :status and event.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("status") OutboxStatus status, @Param("cutoff") LocalDateTime cutoff);
}
