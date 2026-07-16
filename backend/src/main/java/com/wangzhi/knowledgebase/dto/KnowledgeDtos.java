package com.wangzhi.knowledgebase.dto;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank String content,
            @NotBlank @Size(max = 80) String domain,
            @NotBlank @Size(max = 120) String source,
            @Size(max = 300) String tags,
            @NotBlank @Size(max = 80) String createdBy
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank String content,
            @NotBlank @Size(max = 80) String domain,
            @NotBlank @Size(max = 120) String source,
            @Size(max = 300) String tags
    ) {}

    public record ReviewRequest(
            boolean approved,
            @NotBlank @Size(max = 80) String reviewer,
            @Size(max = 500) String comment
    ) {}

    public record View(
            Long id,
            String title,
            String content,
            String domain,
            String source,
            List<String> tags,
            KnowledgeStatus status,
            String reviewComment,
            String reviewer,
            int chunkCount,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version
    ) {
        public static View from(KnowledgeDocument document) {
            List<String> parsedTags = document.getTags() == null || document.getTags().isBlank()
                    ? List.of()
                    : List.of(document.getTags().split("\\s*,\\s*"));
            return new View(
                    document.getId(), document.getTitle(), document.getContent(), document.getDomain(),
                    document.getSource(), parsedTags, document.getStatus(), document.getReviewComment(),
                    document.getReviewer(), document.getChunkCount(), document.getCreatedBy(),
                    document.getCreatedAt(), document.getUpdatedAt(), document.getVersion()
            );
        }
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ImportResult(int imported, int failed, List<String> errors) {}
}
