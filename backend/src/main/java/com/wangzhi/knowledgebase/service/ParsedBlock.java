package com.wangzhi.knowledgebase.service;

public record ParsedBlock(
        String type,
        String text,
        int pageNumber,
        String headingPath,
        String locator
) {}
