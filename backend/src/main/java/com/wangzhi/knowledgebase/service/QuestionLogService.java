package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.Source;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import com.wangzhi.knowledgebase.security.DomainAccessService;
import com.wangzhi.knowledgebase.security.SecurityIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionLogService {

    private final QuestionLogRepository logRepository;
    private final DomainAccessService domainAccessService;
    private final SecurityIdentity securityIdentity;

    public QuestionLogService(QuestionLogRepository logRepository,
                              DomainAccessService domainAccessService,
                              SecurityIdentity securityIdentity) {
        this.logRepository = logRepository;
        this.domainAccessService = domainAccessService;
        this.securityIdentity = securityIdentity;
    }

    @Transactional
    public void record(String traceId, String question, String domain, ChatResult result, double confidence,
                       long latencyMs, List<Source> sources, boolean noRetrieval) {
        QuestionLog log = new QuestionLog();
        log.setTraceId(traceId);
        log.setAskedBy(securityIdentity.current());
        log.setDomain(domain == null || domain.isBlank() ? null : domain.trim());
        log.setQuestion(question.trim());
        log.setAnswer(result.answer());
        log.setConfidence(confidence);
        log.setLatencyMs(latencyMs);
        log.setBadCase(noRetrieval || confidence < 0.55 || result.fallback());
        if (noRetrieval) {
            log.setBadReason("未召回有效知识");
        } else if (result.fallback()) {
            log.setBadReason("生成模型不可用或引用校验失败，已降级");
        }
        log.setSourceSnapshot(writeSources(sources));
        log.setModelName(result.model());
        log.setPromptVersion(result.promptVersion());
        log.setInputTokens(result.inputTokens());
        log.setOutputTokens(result.outputTokens());
        log.setFallback(result.fallback());
        logRepository.save(log);
    }

    @Transactional
    public void feedback(String traceId, FeedbackRequest request) {
        QuestionLog log = logRepository.findByTraceId(traceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "问答追踪记录不存在"));
        domainAccessService.check(log.getDomain());
        if (domainAccessService.securityEnabled() && !domainAccessService.isAdmin()
                && !securityIdentity.current().equals(log.getAskedBy())) {
            throw new AccessDeniedException("不能修改其他用户的问答反馈");
        }
        log.setAccepted(request.accepted());
        if (!request.accepted()) {
            log.setBadCase(true);
            log.setBadReason(request.reason() == null || request.reason().isBlank() ? "用户反馈未解决" : request.reason().trim());
        } else if (log.getConfidence() >= 0.55 && !log.isFallback()) {
            log.setBadCase(false);
            log.setBadReason(null);
        }
        logRepository.save(log);
    }

    private String writeSources(List<Source> sources) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < sources.size(); index++) {
            Source source = sources.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"documentId\":").append(source.documentId())
                    .append(",\"title\":\"").append(escapeJson(source.title()))
                    .append("\",\"domain\":\"").append(escapeJson(source.domain()))
                    .append("\",\"excerpt\":\"").append(escapeJson(source.excerpt()))
                    .append("\",\"score\":").append(source.score()).append('}');
        }
        return json.append(']').toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
