package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.Source;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionLogService {

    private final QuestionLogRepository logRepository;

    public QuestionLogService(QuestionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional
    public void record(String traceId, String question, String domain, ChatResult result, double confidence,
                       long latencyMs, List<Source> sources, boolean noRetrieval) {
        QuestionLog log = new QuestionLog();
        log.setTraceId(traceId);
        log.setAskedBy("本地用户");
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
