package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long>,
        JpaSpecificationExecutor<KnowledgeDocument> {

    List<KnowledgeDocument> findByStatus(KnowledgeStatus status);

    List<KnowledgeDocument> findByIdIn(Collection<Long> ids);

    long countByStatus(KnowledgeStatus status);
}
