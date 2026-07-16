package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import com.wangzhi.knowledgebase.dto.ImportDtos.BatchView;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import com.wangzhi.knowledgebase.repository.ImportFileTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportBatchService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "doc", "docx", "pdf", "xls", "xlsx", "csv", "tsv", "txt", "md", "rtf",
            "ppt", "pptx", "png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp"
    );

    private final ImportBatchRepository batchRepository;
    private final ImportFileTaskRepository fileRepository;
    private final ImportProcessor importProcessor;
    private final KnowledgeService knowledgeService;
    private final Path storageRoot;
    private final int maxFilesPerBatch;

    public ImportBatchService(ImportBatchRepository batchRepository,
                              ImportFileTaskRepository fileRepository,
                              ImportProcessor importProcessor,
                              KnowledgeService knowledgeService,
                              @Value("${app.import.storage-path:./data/imports}") String storagePath,
                              @Value("${app.import.max-files-per-batch:50}") int maxFilesPerBatch) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.importProcessor = importProcessor;
        this.knowledgeService = knowledgeService;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxFilesPerBatch = maxFilesPerBatch;
    }

    @Transactional
    public BatchView create(MultipartFile[] files, String domain, String createdBy, String tags) {
        if (files == null || files.length == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请至少选择一个文件");
        }
        if (files.length > maxFilesPerBatch) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "单批次最多上传 " + maxFilesPerBatch + " 个文件");
        }
        if (domain == null || domain.isBlank() || createdBy == null || createdBy.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "业务领域和创建人不能为空");
        }
        validateFiles(files);
        String batchId = "IMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        Path batchDirectory = storageRoot.resolve(batchId);
        try {
            Files.createDirectories(batchDirectory);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "导入存储目录创建失败");
        }

        ImportBatch batch = new ImportBatch();
        batch.setId(batchId);
        batch.setStatus(ImportBatchStatus.QUEUED);
        batch.setDomain(domain.trim());
        batch.setCreatedBy(createdBy.trim());
        batch.setTags(tags == null || tags.isBlank() ? null : tags.trim());
        batch.setTotalFiles(files.length);
        batchRepository.save(batch);

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            String originalName = safeOriginalName(file.getOriginalFilename());
            String storedName = "%03d_%s".formatted(index + 1, originalName);
            Path target = batchDirectory.resolve(storedName).normalize();
            if (!target.startsWith(batchDirectory)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件名不合法");
            }
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败：" + originalName);
            }
            ImportFileTask task = new ImportFileTask();
            task.setBatchId(batchId);
            task.setOriginalName(originalName);
            task.setStoredPath(target.toString());
            task.setSizeBytes(file.getSize());
            task.setStatus(ImportFileStatus.QUEUED);
            fileRepository.save(task);
        }
        schedule(batchId, false);
        return get(batchId);
    }

    @Transactional(readOnly = true)
    public BatchView get(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        return BatchView.from(batch, fileRepository.findByBatchIdOrderByIdAsc(batchId));
    }

    @Transactional(readOnly = true)
    public List<BatchView> recent() {
        return batchRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(batch -> BatchView.from(batch, fileRepository.findByBatchIdOrderByIdAsc(batch.getId())))
                .toList();
    }

    @Transactional
    public BatchView retry(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        List<ImportFileTask> failed = fileRepository.findByBatchIdAndStatus(batchId, ImportFileStatus.FAILED);
        if (failed.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前批次没有失败文件");
        }
        failed.forEach(task -> {
            task.setStatus(ImportFileStatus.QUEUED);
            task.setErrorMessage(null);
            task.setRetryCount(task.getRetryCount() + 1);
        });
        fileRepository.saveAll(failed);
        batch.setStatus(ImportBatchStatus.QUEUED);
        batch.setProcessedFiles(batch.getProcessedFiles() - failed.size());
        batch.setFailedFiles(0);
        batchRepository.save(batch);
        schedule(batchId, true);
        return get(batchId);
    }

    @Transactional
    public BatchView submit(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        if (batch.getStatus() != ImportBatchStatus.READY && batch.getStatus() != ImportBatchStatus.PARTIAL_READY) {
            throw new BusinessException(HttpStatus.CONFLICT, "批次尚未处理完成，不能提交审核");
        }
        List<ImportFileTask> files = fileRepository.findByBatchIdAndStatus(batchId, ImportFileStatus.READY);
        if (files.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "没有可提交的知识");
        }
        for (ImportFileTask file : files) {
            knowledgeService.submit(file.getDocumentId());
            file.setStatus(ImportFileStatus.SUBMITTED);
        }
        fileRepository.saveAll(files);
        batch.setStatus(ImportBatchStatus.SUBMITTED);
        batchRepository.save(batch);
        return get(batchId);
    }

    @Transactional(readOnly = true)
    public byte[] report(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        StringBuilder csv = new StringBuilder("batchId,fileName,fileType,sizeBytes,status,characters,documentId,retries,error\n");
        for (ImportFileTask file : fileRepository.findByBatchIdOrderByIdAsc(batchId)) {
            csv.append(csv(batch.getId())).append(',').append(csv(file.getOriginalName())).append(',')
                    .append(csv(file.getDetectedType())).append(',').append(file.getSizeBytes()).append(',')
                    .append(file.getStatus()).append(',').append(file.getExtractedCharacters()).append(',')
                    .append(file.getDocumentId() == null ? "" : file.getDocumentId()).append(',')
                    .append(file.getRetryCount()).append(',').append(csv(file.getErrorMessage())).append('\n');
        }
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    private void validateFiles(MultipartFile[] files) {
        Arrays.stream(files).forEach(file -> {
            String name = safeOriginalName(file.getOriginalFilename());
            if (file.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件为空：" + name);
            }
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "暂不支持该格式：" + name);
            }
        });
    }

    private String safeOriginalName(String value) {
        String name = value == null || value.isBlank() ? "unnamed.txt" : Path.of(value).getFileName().toString();
        return name.replaceAll("[\\r\\n\\t]", "_");
    }

    private ImportBatch requireBatch(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "导入批次不存在"));
    }

    private void schedule(String batchId, boolean retry) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    importProcessor.process(batchId, retry);
                }
            });
        } else {
            importProcessor.process(batchId, retry);
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
