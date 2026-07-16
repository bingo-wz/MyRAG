package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.BadCaseView;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.DailyPoint;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.Overview;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final KnowledgeDocumentRepository documentRepository;
    private final QuestionLogRepository logRepository;

    public AnalyticsService(KnowledgeDocumentRepository documentRepository, QuestionLogRepository logRepository) {
        this.documentRepository = documentRepository;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<QuestionLog> logs = logRepository.findByCreatedAtAfterOrderByCreatedAtAsc(LocalDate.now().minusDays(6).atStartOfDay());
        long accepted = logs.stream().filter(log -> Boolean.TRUE.equals(log.getAccepted())).count();
        long rated = logs.stream().filter(log -> log.getAccepted() != null).count();
        double acceptanceRate = rated == 0 ? 0 : accepted * 100.0 / rated;
        double averageConfidence = logs.stream().mapToDouble(QuestionLog::getConfidence).average().orElse(0) * 100;
        long averageLatency = Math.round(logs.stream().mapToLong(QuestionLog::getLatencyMs).average().orElse(0));
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

        Map<String, Long> distribution = documentRepository.findAll().stream()
                .collect(Collectors.groupingBy(KnowledgeDocument::getDomain, LinkedHashMap::new, Collectors.counting()));
        return new Overview(documentRepository.count(), documentRepository.countByStatus(KnowledgeStatus.APPROVED),
                documentRepository.countByStatus(KnowledgeStatus.PENDING_REVIEW), logs.size(), round(acceptanceRate),
                round(averageConfidence), averageLatency, badCases, trend, distribution);
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
}
