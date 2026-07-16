package com.wangzhi.knowledgebase.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record Overview(
            long totalKnowledge,
            long approvedKnowledge,
            long pendingReview,
            long questionCount,
            double acceptanceRate,
            double averageConfidence,
            long averageLatencyMs,
            long badCaseCount,
            List<DailyPoint> trend,
            Map<String, Long> domainDistribution
    ) {}

    public record DailyPoint(LocalDate date, long questions, double acceptanceRate) {}

    public record BadCaseView(
            String traceId,
            String question,
            String answer,
            double confidence,
            long latencyMs,
            String reason,
            String sourceSnapshot,
            LocalDateTime createdAt
    ) {}
}
