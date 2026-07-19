package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import com.wangzhi.knowledgebase.repository.ImportFileTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ImportFileTaskStateService {

    private static final List<ImportFileStatus> INTERRUPTED = List.of(
            ImportFileStatus.DETECTING,
            ImportFileStatus.EXTRACTING,
            ImportFileStatus.VALIDATING,
            ImportFileStatus.INDEXING
    );
    private static final List<ImportFileStatus> RECOVERABLE = List.of(
            ImportFileStatus.QUEUED,
            ImportFileStatus.DETECTING,
            ImportFileStatus.EXTRACTING,
            ImportFileStatus.VALIDATING,
            ImportFileStatus.INDEXING
    );

    private final ImportFileTaskRepository repository;

    public ImportFileTaskStateService(ImportFileTaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int requeueInterrupted(String batchId) {
        return repository.requeueInterrupted(batchId, INTERRUPTED, ImportFileStatus.QUEUED,
                "Worker 中断，文件已自动重新排队");
    }

    @Transactional
    public void failRecoverable(String batchId) {
        repository.failRecoverable(batchId, RECOVERABLE, ImportFileStatus.FAILED,
                "批次超过最大自动恢复次数");
    }

    @Transactional
    public Optional<ImportFileTask> claim(Long taskId) {
        int updated = repository.claim(taskId, ImportFileStatus.QUEUED, ImportFileStatus.DETECTING);
        return updated == 1 ? repository.findById(taskId) : Optional.empty();
    }
}
