package com.wangzhi.knowledgebase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private final int chunkSize;

    public ChunkingService(@Value("${app.retrieval.chunk-size:420}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : normalized.split("(?<=[。！？；.!?;\\n])")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > chunkSize) {
                chunks.add(current.toString().trim());
                current = new StringBuilder(overlap(current.toString()));
            }
            if (sentence.length() > chunkSize) {
                int offset = 0;
                while (offset < sentence.length()) {
                    int end = Math.min(sentence.length(), offset + chunkSize);
                    chunks.add(sentence.substring(offset, end).trim());
                    if (end == sentence.length()) {
                        break;
                    }
                    offset = Math.max(0, end - 60);
                }
            } else {
                current.append(sentence);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks.stream().filter(item -> !item.isBlank()).toList();
    }

    private String overlap(String text) {
        int start = Math.max(0, text.length() - 60);
        return text.substring(start);
    }
}
