package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {
    List<ImportBatch> findTop20ByOrderByCreatedAtDesc();
}
