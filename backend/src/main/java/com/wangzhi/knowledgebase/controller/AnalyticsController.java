package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.dto.AnalyticsDtos.BadCaseView;
import com.wangzhi.knowledgebase.dto.AnalyticsDtos.Overview;
import com.wangzhi.knowledgebase.service.AnalyticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
@ConditionalOnProperty(name = "app.runtime.api-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return analyticsService.overview();
    }

    @GetMapping("/bad-cases")
    public List<BadCaseView> badCases() {
        return analyticsService.badCases();
    }
}
