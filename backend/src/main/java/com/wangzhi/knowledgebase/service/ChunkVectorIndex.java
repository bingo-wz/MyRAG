package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;

import java.util.List;

public interface ChunkVectorIndex {

    boolean inlineEmbedding();

    void index(List<KnowledgeChunk> chunks, List<double[]> vectors);

    void deleteDocument(Long documentId);

    default void updateDocumentStatus(Long documentId, KnowledgeStatus status) {
        // 仅远程向量库需要同步状态过滤字段。
    }
}
