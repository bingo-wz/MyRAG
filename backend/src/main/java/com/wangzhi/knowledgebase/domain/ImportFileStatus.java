package com.wangzhi.knowledgebase.domain;

public enum ImportFileStatus {
    QUEUED,
    DETECTING,
    EXTRACTING,
    VALIDATING,
    INDEXING,
    READY,
    SUBMITTED,
    FAILED
}
