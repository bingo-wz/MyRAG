package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ImportResult;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.PageResponse;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ReviewRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.UpdateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.View;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public PageResponse<View> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) KnowledgeStatus status,
                                     @RequestParam(required = false) String domain,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return knowledgeService.search(keyword, status, domain, page, size);
    }

    @GetMapping("/{id}")
    public View get(@PathVariable Long id) {
        return knowledgeService.get(id);
    }

    @PostMapping
    public View create(@Valid @RequestBody CreateRequest request) {
        return knowledgeService.create(request);
    }

    @PutMapping("/{id}")
    public View update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return knowledgeService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    public View submit(@PathVariable Long id) {
        return knowledgeService.submit(id);
    }

    @PostMapping("/{id}/review")
    public View review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return knowledgeService.review(id, request);
    }

    @PostMapping("/{id}/offline")
    public View offline(@PathVariable Long id) {
        return knowledgeService.offline(id);
    }

    @PostMapping("/{id}/reactivate")
    public View reactivate(@PathVariable Long id) {
        return knowledgeService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "批量导入") String createdBy) {
        return knowledgeService.importCsv(file, createdBy);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) KnowledgeStatus status) {
        String filename = java.net.URLEncoder.encode("知识库导出.csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(knowledgeService.exportCsv(status));
    }
}
