package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.QaDtos.AskRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.AskResponse;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.Source;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import com.wangzhi.knowledgebase.service.RetrievalService.RetrievedChunk;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QaService {

    private final RetrievalService retrievalService;
    private final QuestionLogRepository logRepository;

    public QaService(RetrievalService retrievalService,
                     QuestionLogRepository logRepository) {
        this.retrievalService = retrievalService;
        this.logRepository = logRepository;
    }

    @Transactional
    public AskResponse ask(AskRequest request) {
        long startedAt = System.nanoTime();
        List<RetrievedChunk> retrieved = retrievalService.retrieve(request.question(), request.domain());
        List<Source> sources = retrieved.stream().map(RetrievedChunk::toSource).toList();
        String answer = buildAnswer(retrieved);
        double confidence = retrieved.isEmpty() ? 0.18
                : Math.min(0.96, 0.48 + Math.max(0, retrieved.getFirst().score()) * 0.52);
        long latencyMs = Math.max(32, (System.nanoTime() - startedAt) / 1_000_000);
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        QuestionLog log = new QuestionLog();
        log.setTraceId(traceId);
        log.setQuestion(request.question().trim());
        log.setAnswer(answer);
        log.setConfidence(confidence);
        log.setLatencyMs(latencyMs);
        log.setBadCase(retrieved.isEmpty() || confidence < 0.55);
        if (retrieved.isEmpty()) {
            log.setBadReason("未召回有效知识");
        }
        log.setSourceSnapshot(writeSources(sources));
        logRepository.save(log);
        return new AskResponse(traceId, answer, round(confidence), latencyMs, sources);
    }

    @Transactional
    public void feedback(String traceId, FeedbackRequest request) {
        QuestionLog log = logRepository.findByTraceId(traceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "问答追踪记录不存在"));
        log.setAccepted(request.accepted());
        if (!request.accepted()) {
            log.setBadCase(true);
            log.setBadReason(request.reason() == null || request.reason().isBlank() ? "用户反馈未解决" : request.reason().trim());
        } else if (log.getConfidence() >= 0.55) {
            log.setBadCase(false);
            log.setBadReason(null);
        }
        logRepository.save(log);
    }

    private String buildAnswer(List<RetrievedChunk> retrieved) {
        if (retrieved.isEmpty()) {
            return "当前已生效知识中没有找到足够可靠的依据。建议换一种问法，或联系知识管理员补充相关资料。";
        }
        StringBuilder answer = new StringBuilder();
        answer.append("根据知识库中已审核生效的内容：\n\n");
        int count = 0;
        for (RetrievedChunk item : retrieved) {
            String text = item.chunk().getContent().replaceAll("\\s+", " ").trim();
            if (text.length() > 220) {
                text = text.substring(0, 220) + "…";
            }
            answer.append(++count).append(". ").append(text).append("\n");
            if (count == 3) {
                break;
            }
        }
        answer.append("\n以上结论来自页面下方列出的知识来源，可展开核对原文。");
        return answer.toString();
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

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
