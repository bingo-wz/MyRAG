package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
