package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import com.wangzhi.knowledgebase.dto.ImportDtos.BatchView;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import com.wangzhi.knowledgebase.repository.ImportFileTaskRepository;
import com.wangzhi.knowledgebase.security.DomainAccessService;
import com.wangzhi.knowledgebase.security.SecurityIdentity;
import com.wangzhi.knowledgebase.storage.ObjectStorageService;
import com.wangzhi.knowledgebase.storage.StoredObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    private final KnowledgeService knowledgeService;
    private final ObjectStorageService storageService;
    private final ImportDispatch importDispatch;
    private final DomainAccessService domainAccessService;
    private final SecurityIdentity securityIdentity;
    private final int maxFilesPerBatch;
    private final long maxFileSizeBytes;
    private final long maxBatchSizeBytes;

    public ImportBatchService(ImportBatchRepository batchRepository,
                              ImportFileTaskRepository fileRepository,
                              KnowledgeService knowledgeService,
                              ObjectStorageService storageService,
                              ImportDispatch importDispatch,
                              DomainAccessService domainAccessService,
                              SecurityIdentity securityIdentity,
                              @Value("${app.import.max-files-per-batch:50}") int maxFilesPerBatch,
                              @Value("${app.import.max-file-size-bytes:20971520}") long maxFileSizeBytes,
                              @Value("${app.import.max-batch-size-bytes:104857600}") long maxBatchSizeBytes) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.knowledgeService = knowledgeService;
        this.storageService = storageService;
        this.importDispatch = importDispatch;
        this.domainAccessService = domainAccessService;
        this.securityIdentity = securityIdentity;
        this.maxFilesPerBatch = maxFilesPerBatch;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxBatchSizeBytes = maxBatchSizeBytes;
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
        domainAccessService.check(domain);
        validateFiles(files);
        String batchId = "IMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        ImportBatch batch = new ImportBatch();
        batch.setId(batchId);
        batch.setStatus(ImportBatchStatus.QUEUED);
        batch.setDomain(domain.trim());
        batch.setCreatedBy(securityIdentity.currentOr(createdBy.trim()));
        batch.setTags(tags == null || tags.isBlank() ? null : tags.trim());
        batch.setTotalFiles(files.length);
        batchRepository.save(batch);

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            String originalName = safeOriginalName(file.getOriginalFilename());
            StoredObject stored;
            try {
                stored = storageService.store(file);
            } catch (IOException exception) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败：" + originalName);
            }
            ImportFileTask task = new ImportFileTask();
            task.setBatchId(batchId);
            task.setOriginalName(originalName);
            task.setStorageKey(stored.key());
            task.setContentHash(stored.sha256());
            task.setStorageDeduplicated(stored.deduplicated());
            task.setSizeBytes(stored.sizeBytes());
            task.setStatus(ImportFileStatus.QUEUED);
            fileRepository.save(task);
        }
        importDispatch.dispatch(batchId);
        return get(batchId);
    }

    @Transactional(readOnly = true)
    public BatchView get(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        domainAccessService.check(batch.getDomain());
        return BatchView.from(batch, fileRepository.findByBatchIdOrderByIdAsc(batchId));
    }

    @Transactional(readOnly = true)
    public List<BatchView> recent() {
        return batchRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .filter(batch -> domainAccessService.allowed(batch.getDomain()))
                .map(batch -> BatchView.from(batch, fileRepository.findByBatchIdOrderByIdAsc(batch.getId())))
                .toList();
    }

    @Transactional
    public BatchView retry(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        domainAccessService.check(batch.getDomain());
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
        batch.setAttemptCount(0);
        batch.setNextAttemptAt(null);
        batch.setWorkerId(null);
        batch.setLeaseUntil(null);
        batch.setLastError(null);
        batchRepository.save(batch);
        importDispatch.dispatch(batchId);
        return get(batchId);
    }

    @Transactional
    public BatchView submit(String batchId) {
        ImportBatch batch = requireBatch(batchId);
        domainAccessService.check(batch.getDomain());
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
        domainAccessService.check(batch.getDomain());
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
        long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        if (totalSize > maxBatchSizeBytes) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "批次文件总大小超过限制");
        }
        Arrays.stream(files).forEach(file -> {
            String name = safeOriginalName(file.getOriginalFilename());
            if (file.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件为空：" + name);
            }
            if (file.getSize() > maxFileSizeBytes) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文件大小超过限制：" + name);
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

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
