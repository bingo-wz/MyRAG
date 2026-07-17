package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.dto.QaDtos.AskRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.AskResponse;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.Source;
import com.wangzhi.knowledgebase.security.DomainAccessService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QaService {

    private final RetrievalService retrievalService;
    private final ChatService chatService;
    private final QuestionLogService logService;
    private final DomainAccessService domainAccessService;

    public QaService(RetrievalService retrievalService,
                     ChatService chatService,
                     QuestionLogService logService,
                     DomainAccessService domainAccessService) {
        this.retrievalService = retrievalService;
        this.chatService = chatService;
        this.logService = logService;
        this.domainAccessService = domainAccessService;
    }

    public AskResponse ask(AskRequest request) {
        domainAccessService.checkSearch(request.domain());
        long startedAt = System.nanoTime();
        List<RetrievedChunk> retrieved = retrievalService.retrieve(request.question(), request.domain());
        List<Source> sources = retrieved.stream().map(RetrievedChunk::toSource).toList();
        ChatResult result = chatService.generate(request.question(), retrieved);
        double confidence = retrieved.isEmpty() ? 0.18
                : Math.min(0.96, 0.48 + Math.max(0, retrieved.getFirst().score()) * 0.52);
        if (result.fallback() && !retrieved.isEmpty()) {
            confidence = Math.min(confidence, 0.62);
        }
        long latencyMs = Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        logService.record(traceId, request.question(), request.domain(), result, confidence,
                latencyMs, sources, retrieved.isEmpty());
        return new AskResponse(traceId, result.answer(), round(confidence), latencyMs, sources,
                result.model(), result.fallback());
    }

    public void feedback(String traceId, FeedbackRequest request) {
        logService.feedback(traceId, request);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
