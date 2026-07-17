package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.dto.QaDtos.AskRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.AskResponse;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.service.QaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
@ConditionalOnProperty(name = "app.runtime.api-enabled", havingValue = "true", matchIfMissing = true)
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN','KNOWLEDGE_OPERATOR','REVIEWER','USER')")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return qaService.ask(request);
    }

    @PostMapping("/{traceId}/feedback")
    @PreAuthorize("hasAnyRole('ADMIN','KNOWLEDGE_OPERATOR','REVIEWER','USER')")
    public ResponseEntity<Void> feedback(@PathVariable String traceId,
                                         @Valid @RequestBody FeedbackRequest request) {
        qaService.feedback(traceId, request);
        return ResponseEntity.noContent().build();
    }
}
