package com.wangzhi.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class QaDtos {

    private QaDtos() {}

    public record AskRequest(
            @NotBlank @Size(max = 1000) String question,
            String domain
    ) {}

    public record Source(
            Long documentId,
            String title,
            String domain,
            String excerpt,
            double score
    ) {}

    public record AskResponse(
            String traceId,
            String answer,
            double confidence,
            long latencyMs,
            List<Source> sources
    ) {}

    public record FeedbackRequest(
            boolean accepted,
            @Size(max = 500) String reason
    ) {}
}
