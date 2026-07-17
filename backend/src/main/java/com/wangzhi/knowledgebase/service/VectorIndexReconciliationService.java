package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnExpression("'${app.vector.reconciliation.enabled:false}' == 'true' and '${app.runtime.api-enabled:true}' == 'true'")
public class VectorIndexReconciliationService {

    private final ChunkVectorIndex vectorIndex;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<IndexReconciliationReport> lastReport = new AtomicReference<>(
            new IndexReconciliationReport(true, 0, 0, 0, 0, 0,
                    LocalDateTime.now(), "尚未执行对账"));

    public VectorIndexReconciliationService(ChunkVectorIndex vectorIndex) {
        this.vectorIndex = vectorIndex;
    }

    @Scheduled(cron = "${app.vector.reconciliation.cron:0 30 3 * * *}")
    public void scheduled() {
        reconcile();
    }

    public IndexReconciliationReport reconcile() {
        if (!running.compareAndSet(false, true)) {
            return new IndexReconciliationReport(true, 0, 0, 0, 0, 0,
                    LocalDateTime.now(), "已有对账任务正在运行");
        }
        try {
            IndexReconciliationReport report = vectorIndex.reconcile();
            lastReport.set(report);
            return report;
        } finally {
            running.set(false);
        }
    }

    public IndexReconciliationReport lastReport() {
        return lastReport.get();
    }
}
