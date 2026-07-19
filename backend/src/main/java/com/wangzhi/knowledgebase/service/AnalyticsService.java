package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.BadCaseView;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.DailyPoint;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.Overview;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
public class AnalyticsService {

    private final KnowledgeDocumentRepository documentRepository;
    private final QuestionLogRepository logRepository;
    private final ImportBatchRepository importBatchRepository;

    public AnalyticsService(KnowledgeDocumentRepository documentRepository,
                            QuestionLogRepository logRepository,
                            ImportBatchRepository importBatchRepository) {
        this.documentRepository = documentRepository;
        this.logRepository = logRepository;
        this.importBatchRepository = importBatchRepository;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        LocalDateTime currentStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<QuestionLog> allLogs = logRepository
                .findByCreatedAtAfterOrderByCreatedAtAsc(currentStart.minusDays(7));
        List<QuestionLog> logs = allLogs.stream()
                .filter(log -> !log.getCreatedAt().isBefore(currentStart))
                .toList();
        List<QuestionLog> previousLogs = allLogs.stream()
                .filter(log -> log.getCreatedAt().isBefore(currentStart))
                .toList();
        long accepted = logs.stream().filter(log -> Boolean.TRUE.equals(log.getAccepted())).count();
        long rated = logs.stream().filter(log -> log.getAccepted() != null).count();
        double acceptanceRate = rated == 0 ? 0 : accepted * 100.0 / rated;
        double previousAcceptanceRate = acceptanceRate(previousLogs);
        double averageConfidence = logs.stream().mapToDouble(QuestionLog::getConfidence).average().orElse(0) * 100;
        long averageLatency = Math.round(logs.stream().mapToLong(QuestionLog::getLatencyMs).average().orElse(0));
        long latencyP95 = percentile95(logs);
        long badCases = logs.stream().filter(QuestionLog::isBadCase).count();

        Map<LocalDate, List<QuestionLog>> dailyGroups = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getCreatedAt().toLocalDate()));
        List<DailyPoint> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            List<QuestionLog> daily = dailyGroups.getOrDefault(date, List.of());
            long dailyRated = daily.stream().filter(log -> log.getAccepted() != null).count();
            long dailyAccepted = daily.stream().filter(log -> Boolean.TRUE.equals(log.getAccepted())).count();
            trend.add(new DailyPoint(date, daily.size(), dailyRated == 0 ? 0 : round(dailyAccepted * 100.0 / dailyRated)));
        }

        List<KnowledgeDocument> documents = documentRepository.findAll();
        Map<String, Long> distribution = documents.stream()
                .collect(Collectors.groupingBy(KnowledgeDocument::getDomain, LinkedHashMap::new, Collectors.counting()));
        Long oldestPendingMinutes = documentRepository.findFirstByStatusOrderByUpdatedAtAsc(KnowledgeStatus.PENDING_REVIEW)
                .map(document -> Math.max(0, Duration.between(document.getUpdatedAt(), LocalDateTime.now()).toMinutes()))
                .orElse(null);
        long activeImportBatches = importBatchRepository.countByStatusIn(
                List.of(ImportBatchStatus.QUEUED, ImportBatchStatus.PROCESSING));
        return new Overview(documents.size(), documents.stream().filter(document -> document.getStatus() == KnowledgeStatus.APPROVED).count(),
                documents.stream().filter(document -> document.getStatus() == KnowledgeStatus.PENDING_REVIEW).count(),
                logs.size(), round(acceptanceRate), round(acceptanceRate - previousAcceptanceRate),
                round(averageConfidence), averageLatency, latencyP95, badCases, oldestPendingMinutes,
                activeImportBatches, trend, distribution);
    }

    @Transactional(readOnly = true)
    public List<BadCaseView> badCases() {
        return logRepository.findByBadCaseTrueOrderByCreatedAtDesc().stream()
                .map(log -> new BadCaseView(log.getTraceId(), log.getQuestion(), log.getAnswer(),
                        round(log.getConfidence() * 100), log.getLatencyMs(), log.getBadReason(),
                        log.getSourceSnapshot(), log.getCreatedAt()))
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double acceptanceRate(List<QuestionLog> logs) {
        long accepted = logs.stream().filter(log -> Boolean.TRUE.equals(log.getAccepted())).count();
        long rated = logs.stream().filter(log -> log.getAccepted() != null).count();
        return rated == 0 ? 0 : accepted * 100.0 / rated;
    }

    private long percentile95(List<QuestionLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }
        List<Long> values = logs.stream().map(QuestionLog::getLatencyMs).sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(values.size() * 0.95) - 1);
        return values.get(index);
    }
}
