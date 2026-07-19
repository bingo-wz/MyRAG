package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long>,
        JpaSpecificationExecutor<KnowledgeDocument> {

    List<KnowledgeDocument> findByStatus(KnowledgeStatus status);

    List<KnowledgeDocument> findByIdIn(Collection<Long> ids);

    Optional<KnowledgeDocument> findByImportTaskId(Long importTaskId);

    Optional<KnowledgeDocument> findFirstByStatusOrderByUpdatedAtAsc(KnowledgeStatus status);

    @org.springframework.data.jpa.repository.Query("select distinct document.domain from KnowledgeDocument document order by document.domain")
    List<String> findDistinctDomains();

    long countByStatus(KnowledgeStatus status);
}
