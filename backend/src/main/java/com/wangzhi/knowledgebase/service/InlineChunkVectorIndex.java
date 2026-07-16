package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.vector.store", havingValue = "inline", matchIfMissing = true)
public class InlineChunkVectorIndex implements ChunkVectorIndex {

    @Override
    public boolean inlineEmbedding() {
        return true;
    }

    @Override
    public void index(List<KnowledgeChunk> chunks, List<double[]> vectors) {
        // 开发模式下向量随 Chunk 一起持久化，无需建立独立索引。
    }

    @Override
    public void deleteDocument(Long documentId) {
        // Chunk 删除时内联向量同步删除。
    }
}
