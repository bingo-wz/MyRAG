package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.dto.QaDtos.Source;

public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String title,
        String domain,
        String content,
        String locator,
        Integer pageNumber,
        double score
) {
    public Source toSource() {
        String excerpt = content.length() > 180 ? content.substring(0, 180) + "…" : content;
        return new Source(documentId, title, domain, excerpt, Math.round(score * 1000.0) / 1000.0);
    }
}
