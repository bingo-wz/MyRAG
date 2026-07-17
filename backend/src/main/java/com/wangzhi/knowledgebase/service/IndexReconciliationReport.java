package com.wangzhi.knowledgebase.service;

import java.time.LocalDateTime;

public record IndexReconciliationReport(
        boolean supported,
        long databaseChunks,
        long vectorRows,
        long repairedRows,
        long repairedStatuses,
        long deletedOrphans,
        LocalDateTime completedAt,
        String message
) {
    public static IndexReconciliationReport unsupported() {
        return new IndexReconciliationReport(false, 0, 0, 0, 0, 0,
                LocalDateTime.now(), "当前向量实现不需要跨存储对账");
    }
}
