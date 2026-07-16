package com.wangzhi.knowledgebase.repository;

import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportFileTaskRepository extends JpaRepository<ImportFileTask, Long> {
    List<ImportFileTask> findByBatchIdOrderByIdAsc(String batchId);
    List<ImportFileTask> findByBatchIdAndStatus(String batchId, ImportFileStatus status);
}
