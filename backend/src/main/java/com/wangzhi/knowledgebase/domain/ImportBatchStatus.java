package com.wangzhi.knowledgebase.domain;

public enum ImportBatchStatus {
    QUEUED,
    PROCESSING,
    READY,
    PARTIAL_READY,
    SUBMITTED,
    FAILED
}
