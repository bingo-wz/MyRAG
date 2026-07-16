package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.QuestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionLogRepository extends JpaRepository<QuestionLog, Long> {
    Optional<QuestionLog> findByTraceId(String traceId);
    List<QuestionLog> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime start);
    List<QuestionLog> findByBadCaseTrueOrderByCreatedAtDesc();
}
