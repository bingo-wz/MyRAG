package com.wangzhi.knowledgebase.dto;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;

import java.time.LocalDateTime;
import java.util.List;

public final class ImportDtos {

    private ImportDtos() {}

    public record FileView(
            Long id,
            String originalName,
            String detectedType,
            long sizeBytes,
            ImportFileStatus status,
            int extractedCharacters,
            Long documentId,
            String errorMessage,
            int retryCount,
            LocalDateTime updatedAt
    ) {
        public static FileView from(ImportFileTask task) {
            return new FileView(task.getId(), task.getOriginalName(), task.getDetectedType(), task.getSizeBytes(),
                    task.getStatus(), task.getExtractedCharacters(), task.getDocumentId(), task.getErrorMessage(),
                    task.getRetryCount(), task.getUpdatedAt());
        }
    }

    public record BatchView(
            String id,
            ImportBatchStatus status,
            String domain,
            String createdBy,
            List<String> tags,
            int totalFiles,
            int processedFiles,
            int succeededFiles,
            int failedFiles,
            int progress,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<FileView> files
    ) {
        public static BatchView from(ImportBatch batch, List<ImportFileTask> files) {
            int progress = batch.getTotalFiles() == 0 ? 0
                    : Math.round(batch.getProcessedFiles() * 100f / batch.getTotalFiles());
            List<String> tags = batch.getTags() == null || batch.getTags().isBlank()
                    ? List.of() : List.of(batch.getTags().split("\\s*,\\s*"));
            return new BatchView(batch.getId(), batch.getStatus(), batch.getDomain(), batch.getCreatedBy(), tags,
                    batch.getTotalFiles(), batch.getProcessedFiles(), batch.getSucceededFiles(), batch.getFailedFiles(),
                    progress, batch.getCreatedAt(), batch.getUpdatedAt(), files.stream().map(FileView::from).toList());
        }
    }
}
