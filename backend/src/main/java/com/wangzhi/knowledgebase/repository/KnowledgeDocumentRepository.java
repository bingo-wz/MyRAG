package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    @Query("""
            select d from KnowledgeDocument d
            where (:keyword is null or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(cast(d.content as string)) like lower(concat('%', :keyword, '%')))
              and (:status is null or d.status = :status)
              and (:domain is null or d.domain = :domain)
            """)
    Page<KnowledgeDocument> search(@Param("keyword") String keyword,
                                   @Param("status") KnowledgeStatus status,
                                   @Param("domain") String domain,
                                   Pageable pageable);

    List<KnowledgeDocument> findByStatus(KnowledgeStatus status);

    List<KnowledgeDocument> findByIdIn(Collection<Long> ids);

    long countByStatus(KnowledgeStatus status);
}
