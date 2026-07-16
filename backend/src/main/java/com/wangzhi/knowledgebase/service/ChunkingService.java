package com.wangzhi.knowledgebase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ChunkingService {

    private final TokenCounter tokenCounter;
    private final int targetTokens;
    private final int overlapTokens;

    public ChunkingService(TokenCounter tokenCounter,
                           @Value("${app.retrieval.target-tokens:480}") int targetTokens,
                           @Value("${app.retrieval.overlap-tokens:60}") int overlapTokens) {
        this.tokenCounter = tokenCounter;
        this.targetTokens = Math.max(100, targetTokens);
        this.overlapTokens = Math.min(Math.max(0, overlapTokens), this.targetTokens / 3);
    }

    public List<String> split(String content) {
        return split("", List.of(new ParsedBlock("paragraph", content == null ? "" : content, 1, "", "manual")))
                .stream().map(ChunkDraft::content).toList();
    }

    public List<ChunkDraft> split(String title, List<ParsedBlock> parsedBlocks) {
        List<Segment> segments = new ArrayList<>();
        for (ParsedBlock block : parsedBlocks) {
            if (block == null || block.text() == null || block.text().isBlank()) {
                continue;
            }
            String contextualized = contextualize(title, block);
            if (tokenCounter.count(contextualized) <= targetTokens) {
                segments.add(new Segment(contextualized, block));
            } else {
                segments.addAll(splitOversized(contextualized, block));
            }
        }
        if (segments.isEmpty()) {
            return List.of();
        }

        List<ChunkDraft> chunks = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        int currentTokens = 0;
        for (Segment segment : segments) {
            int segmentTokens = tokenCounter.count(segment.text());
            if (!current.isEmpty() && currentTokens + segmentTokens > targetTokens) {
                chunks.add(toDraft(current));
                current = overlapTail(current);
                currentTokens = current.stream().mapToInt(item -> tokenCounter.count(item.text())).sum();
                if (currentTokens + segmentTokens > targetTokens) {
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
            }
            current.add(segment);
            currentTokens += segmentTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(toDraft(current));
        }
        return chunks.stream().filter(chunk -> !chunk.content().isBlank()).toList();
    }

    private List<Segment> splitOversized(String text, ParsedBlock block) {
        List<Segment> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = tokenCounter.endOffsetForTokens(text, start, targetTokens);
            result.add(new Segment(text.substring(start, end).trim(), block));
            if (end >= text.length()) {
                break;
            }
            int next = tokenCounter.endOffsetForTokens(text, start, Math.max(1, targetTokens - overlapTokens));
            start = Math.max(start + 1, Math.min(next, end));
        }
        return result;
    }

    private List<Segment> overlapTail(List<Segment> segments) {
        List<Segment> tail = new ArrayList<>();
        int tokens = 0;
        for (int index = segments.size() - 1; index >= 0; index--) {
            Segment segment = segments.get(index);
            int segmentTokens = tokenCounter.count(segment.text());
            if (!tail.isEmpty() && tokens + segmentTokens > overlapTokens) {
                break;
            }
            tail.addFirst(segment);
            tokens += segmentTokens;
            if (tokens >= overlapTokens) {
                break;
            }
        }
        return tail;
    }

    private ChunkDraft toDraft(List<Segment> segments) {
        String content = segments.stream().map(Segment::text).reduce((left, right) -> left + "\n\n" + right).orElse("");
        ParsedBlock first = segments.getFirst().source();
        ParsedBlock last = segments.getLast().source();
        String locator = first.locator().equals(last.locator())
                ? first.locator() : first.locator() + ".." + last.locator();
        return new ChunkDraft(content, tokenCounter.count(content), first.headingPath(), locator,
                first.pageNumber(), sha256(content));
    }

    private String contextualize(String title, ParsedBlock block) {
        StringBuilder prefix = new StringBuilder();
        if (title != null && !title.isBlank()) {
            prefix.append(title.trim());
        }
        if (block.headingPath() != null && !block.headingPath().isBlank()
                && !block.headingPath().equals(title)) {
            if (!prefix.isEmpty()) prefix.append(" > ");
            prefix.append(block.headingPath());
        }
        if (prefix.isEmpty() || block.text().equals(prefix.toString())) {
            return block.text().trim();
        }
        return prefix + "\n" + block.text().trim();
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private record Segment(String text, ParsedBlock source) {}

    public record ChunkDraft(
            String content,
            int tokenCount,
            String headingPath,
            String locator,
            int pageNumber,
            String contentHash
    ) {}
}
