package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findByDocumentIdIn(Collection<Long> documentIds);
    void deleteByDocumentId(Long documentId);
}
