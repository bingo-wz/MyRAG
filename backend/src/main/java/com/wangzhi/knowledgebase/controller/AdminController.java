package com.wangzhi.knowledgebase.controller;

import com.wangzhi.knowledgebase.service.IndexReconciliationReport;
import com.wangzhi.knowledgebase.service.VectorIndexReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/index")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnExpression("'${app.vector.reconciliation.enabled:false}' == 'true' and '${app.runtime.api-enabled:true}' == 'true'")
public class AdminController {

    private final VectorIndexReconciliationService reconciliationService;

    public AdminController(VectorIndexReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/reconcile")
    public IndexReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }

    @GetMapping("/reconcile")
    public IndexReconciliationReport lastReport() {
        return reconciliationService.lastReport();
    }
}
