package com.wangzhi.knowledgebase.service;

public record ChatResult(
        String answer,
        String model,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        boolean fallback
) {}
