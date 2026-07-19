package com.wangzhi.knowledgebase.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.audit.enabled", havingValue = "true")
public class ApiAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAuditFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogService auditLogService;

    public ApiAuditFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (MUTATING_METHODS.contains(request.getMethod()) && request.getRequestURI().startsWith("/api/")) {
                record(request, response);
            }
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        try {
            auditLogService.record("本地用户", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (RuntimeException exception) {
            log.error("审计日志写入失败，method={}，path={}", request.getMethod(), request.getRequestURI(), exception);
        }
    }
}
