package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.dto.QaDtos.AskRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.AskResponse;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.service.QaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return qaService.ask(request);
    }

    @PostMapping("/{traceId}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable String traceId,
                                         @Valid @RequestBody FeedbackRequest request) {
        qaService.feedback(traceId, request);
        return ResponseEntity.noContent().build();
    }
}
