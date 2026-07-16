package com.wangzhi.knowledgebase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final RetrievalBackend backend;
    private final int topK;
    private final double minScore;

    public RetrievalService(RetrievalBackend backend,
                            @Value("${app.retrieval.top-k:4}") int topK,
                            @Value("${app.retrieval.min-score:0.08}") double minScore) {
        this.backend = backend;
        this.topK = topK;
        this.minScore = minScore;
    }

    public List<RetrievedChunk> retrieve(String question, String domain) {
        return backend.retrieve(question, domain, topK, minScore);
    }
}
