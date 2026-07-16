package com.wangzhi.knowledgebase.storage;

public record StoredObject(
        String key,
        String sha256,
        long sizeBytes,
        boolean deduplicated
) {}
