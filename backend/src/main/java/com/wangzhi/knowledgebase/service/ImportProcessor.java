package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.View;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import com.wangzhi.knowledgebase.repository.ImportFileTaskRepository;
import com.wangzhi.knowledgebase.service.TextExtractionService.ExtractionResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ImportProcessor {

    private final ImportBatchRepository batchRepository;
    private final ImportFileTaskRepository fileRepository;
    private final TextExtractionService extractionService;
    private final KnowledgeService knowledgeService;

    public ImportProcessor(ImportBatchRepository batchRepository,
                           ImportFileTaskRepository fileRepository,
                           TextExtractionService extractionService,
                           KnowledgeService knowledgeService) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.extractionService = extractionService;
        this.knowledgeService = knowledgeService;
    }

    @Async("importExecutor")
    public void process(String batchId, boolean retry) {
        ImportBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        batch.setStatus(ImportBatchStatus.PROCESSING);
        batchRepository.save(batch);
        List<ImportFileTask> tasks = fileRepository.findByBatchIdAndStatus(batchId, ImportFileStatus.QUEUED);
        for (ImportFileTask task : tasks) {
            processOne(batch, task);
            refreshProgress(batchId);
        }
        finish(batchId);
    }

    private void processOne(ImportBatch batch, ImportFileTask task) {
        try {
            update(task, ImportFileStatus.DETECTING);
            Path path = Path.of(task.getStoredPath());

            update(task, ImportFileStatus.EXTRACTING);
            ExtractionResult result = extractionService.extract(path, task.getOriginalName());
            task.setDetectedType(result.contentType());
            task.setExtractedCharacters(result.text().length());
            fileRepository.save(task);

            update(task, ImportFileStatus.VALIDATING);
            validate(result.text());

            update(task, ImportFileStatus.INDEXING);
            CreateRequest request = new CreateRequest(titleOf(task.getOriginalName()), result.text(), batch.getDomain(),
                    "批量导入 · " + task.getOriginalName(), batch.getTags(), batch.getCreatedBy());
            View document = knowledgeService.create(request);
            task.setDocumentId(document.id());
            task.setStatus(ImportFileStatus.READY);
            task.setErrorMessage(null);
            fileRepository.save(task);
        } catch (Exception exception) {
            task.setStatus(ImportFileStatus.FAILED);
            task.setErrorMessage(rootMessage(exception));
            fileRepository.save(task);
        }
    }

    private void validate(String text) {
        if (text == null || text.length() < 20) {
            throw new IllegalArgumentException("有效正文少于 20 个字符");
        }
        long replacementCharacters = text.chars().filter(value -> value == 0xfffd).count();
        if (replacementCharacters > Math.max(5, text.length() / 20)) {
            throw new IllegalArgumentException("文档乱码比例过高，请检查编码或文件完整性");
        }
    }

    private void update(ImportFileTask task, ImportFileStatus status) {
        task.setStatus(status);
        fileRepository.save(task);
    }

    private void refreshProgress(String batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow();
        List<ImportFileTask> all = fileRepository.findByBatchIdOrderByIdAsc(batchId);
        long succeeded = all.stream().filter(task -> task.getStatus() == ImportFileStatus.READY
                || task.getStatus() == ImportFileStatus.SUBMITTED).count();
        long failed = all.stream().filter(task -> task.getStatus() == ImportFileStatus.FAILED).count();
        batch.setSucceededFiles((int) succeeded);
        batch.setFailedFiles((int) failed);
        batch.setProcessedFiles((int) (succeeded + failed));
        batchRepository.save(batch);
    }

    private void finish(String batchId) {
        refreshProgress(batchId);
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getSucceededFiles() == 0) {
            batch.setStatus(ImportBatchStatus.FAILED);
        } else if (batch.getFailedFiles() > 0) {
            batch.setStatus(ImportBatchStatus.PARTIAL_READY);
        } else {
            batch.setStatus(ImportBatchStatus.READY);
        }
        batchRepository.save(batch);
    }

    private String titleOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message.length() > 780 ? message.substring(0, 780) : message;
    }
}
