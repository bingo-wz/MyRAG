package com.wangzhi.knowledgebase.audit;

import com.wangzhi.knowledgebase.domain.AuditLog;
import com.wangzhi.knowledgebase.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String method, String path, int status, String remoteAddress, String userAgent) {
        AuditLog log = new AuditLog();
        log.setActor(limit(actor, 120));
        log.setMethod(limit(method, 12));
        log.setPath(limit(path, 500));
        log.setResponseStatus(status);
        log.setRemoteAddress(limit(remoteAddress, 80));
        log.setUserAgent(limit(userAgent, 300));
        repository.save(log);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
