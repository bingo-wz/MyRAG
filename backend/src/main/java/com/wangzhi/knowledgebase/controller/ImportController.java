package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.dto.ImportDtos.BatchView;
import com.wangzhi.knowledgebase.service.ImportBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/imports")
@ConditionalOnProperty(name = "app.runtime.api-enabled", havingValue = "true", matchIfMissing = true)
public class ImportController {

    private final ImportBatchService importBatchService;

    public ImportController(ImportBatchService importBatchService) {
        this.importBatchService = importBatchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchView create(@RequestPart("files") MultipartFile[] files,
                            @RequestParam String domain,
                            @RequestParam(defaultValue = "知识运营") String createdBy,
                            @RequestParam(required = false) String tags) {
        return importBatchService.create(files, domain, createdBy, tags);
    }

    @GetMapping
    public List<BatchView> recent() {
        return importBatchService.recent();
    }

    @GetMapping("/{batchId}")
    public BatchView get(@PathVariable String batchId) {
        return importBatchService.get(batchId);
    }

    @PostMapping("/{batchId}/retry")
    public BatchView retry(@PathVariable String batchId) {
        return importBatchService.retry(batchId);
    }

    @PostMapping("/{batchId}/submit")
    public BatchView submit(@PathVariable String batchId) {
        return importBatchService.submit(batchId);
    }

    @GetMapping(value = "/{batchId}/report", produces = "text/csv")
    public ResponseEntity<byte[]> report(@PathVariable String batchId) {
        String filename = java.net.URLEncoder.encode(batchId + "_导入报告.csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(importBatchService.report(batchId));
    }
}
