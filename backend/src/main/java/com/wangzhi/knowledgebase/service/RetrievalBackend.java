package com.wangzhi.knowledgebase.service;

import java.util.List;

public interface RetrievalBackend {
    List<RetrievedChunk> retrieve(String question, String domain, int topK, double minScore);
}
