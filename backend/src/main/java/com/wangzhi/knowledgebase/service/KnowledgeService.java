package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.common.BusinessException;
import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ImportResult;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.PageResponse;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ReviewRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.UpdateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.View;
import com.wangzhi.knowledgebase.repository.KnowledgeChunkRepository;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import com.wangzhi.knowledgebase.service.ChunkingService.ChunkDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ChunkVectorIndex vectorIndex;

    public KnowledgeService(KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            ChunkingService chunkingService,
                            EmbeddingService embeddingService,
                            ChunkVectorIndex vectorIndex) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
    }

    @Transactional(readOnly = true)
    public PageResponse<View> search(String keyword, KnowledgeStatus status, String domain, int page, int size) {
        String normalizedKeyword = blankToNull(keyword);
        String normalizedDomain = blankToNull(domain);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Specification<KnowledgeDocument>> filters = new ArrayList<>();
        if (normalizedKeyword != null) {
            String pattern = likePattern(normalizedKeyword);
            filters.add((root, query, criteria) -> criteria.or(
                    criteria.like(criteria.lower(root.get("title")), pattern, '\\'),
                    criteria.like(criteria.lower(root.get("content")), pattern, '\\')));
        }
        if (status != null) {
            filters.add((root, query, criteria) -> criteria.equal(root.get("status"), status));
        }
        if (normalizedDomain != null) {
            filters.add((root, query, criteria) -> criteria.equal(root.get("domain"), normalizedDomain));
        }
        Page<KnowledgeDocument> result = documentRepository.findAll(Specification.allOf(filters), pageable);
        return new PageResponse<>(result.getContent().stream().map(View::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @Transactional(readOnly = true)
    public View get(Long id) {
        return View.from(requireDocument(id));
    }

    @Transactional
    public View create(CreateRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        apply(document, request.title(), request.content(), request.domain(), request.source(), request.tags());
        document.setCreatedBy(request.createdBy().trim());
        return View.from(saveAndIndex(document, defaultBlocks(request.title(), request.content())));
    }

    @Transactional
    public View createImported(CreateRequest request, List<ParsedBlock> blocks, String sourceHash) {
        KnowledgeDocument document = new KnowledgeDocument();
        apply(document, request.title(), request.content(), request.domain(), request.source(), request.tags());
        document.setCreatedBy(request.createdBy().trim());
        document.setSourceHash(sourceHash);
        return View.from(saveAndIndex(document, blocks));
    }

    @Transactional
    public View update(Long id, UpdateRequest request) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() == KnowledgeStatus.PENDING_REVIEW || document.getStatus() == KnowledgeStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "待审核或已生效知识不能直接编辑，请先下线");
        }
        apply(document, request.title(), request.content(), request.domain(), request.source(), request.tags());
        document.setStatus(KnowledgeStatus.DRAFT);
        document.setReviewComment(null);
        return View.from(saveAndIndex(document, defaultBlocks(request.title(), request.content())));
    }

    @Transactional
    public View submit(Long id) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() != KnowledgeStatus.DRAFT && document.getStatus() != KnowledgeStatus.REJECTED) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿或驳回状态可以提交审核");
        }
        document.setStatus(KnowledgeStatus.PENDING_REVIEW);
        document.setReviewComment(null);
        KnowledgeDocument saved = documentRepository.save(document);
        vectorIndex.updateDocumentStatus(saved.getId(), saved.getStatus());
        return View.from(saved);
    }

    @Transactional
    public View review(Long id, ReviewRequest request) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() != KnowledgeStatus.PENDING_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "该知识不在待审核状态");
        }
        document.setStatus(request.approved() ? KnowledgeStatus.APPROVED : KnowledgeStatus.REJECTED);
        document.setReviewer(request.reviewer().trim());
        document.setReviewComment(blankToNull(request.comment()));
        KnowledgeDocument saved = documentRepository.save(document);
        vectorIndex.updateDocumentStatus(saved.getId(), saved.getStatus());
        return View.from(saved);
    }

    @Transactional
    public View offline(Long id) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() != KnowledgeStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有已生效知识可以下线");
        }
        document.setStatus(KnowledgeStatus.OFFLINE);
        KnowledgeDocument saved = documentRepository.save(document);
        vectorIndex.updateDocumentStatus(saved.getId(), saved.getStatus());
        return View.from(saved);
    }

    @Transactional
    public View reactivate(Long id) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() != KnowledgeStatus.OFFLINE) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有已下线知识可以重新提交");
        }
        document.setStatus(KnowledgeStatus.PENDING_REVIEW);
        KnowledgeDocument saved = documentRepository.save(document);
        vectorIndex.updateDocumentStatus(saved.getId(), saved.getStatus());
        return View.from(saved);
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择 CSV 文件");
        }
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            List<List<String>> rows = parseCsv(text);
            int start = !rows.isEmpty() && !rows.getFirst().isEmpty()
                    && rows.getFirst().getFirst().toLowerCase(Locale.ROOT).contains("title") ? 1 : 0;
            for (int index = start; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                if (row.size() < 4 || row.get(0).isBlank() || row.get(1).isBlank()) {
                    errors.add("第 " + (index + 1) + " 行字段不足");
                    continue;
                }
                CreateRequest request = new CreateRequest(row.get(0), row.get(1), row.get(2), row.get(3),
                        row.size() > 4 ? row.get(4) : "", blankToNull(createdBy) == null ? "批量导入" : createdBy);
                create(request);
                imported++;
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件读取失败");
        }
        return new ImportResult(imported, errors.size(), errors.stream().limit(20).toList());
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(KnowledgeStatus status) {
        List<KnowledgeDocument> documents = status == null ? documentRepository.findAll() : documentRepository.findByStatus(status);
        StringBuilder csv = new StringBuilder("title,content,domain,source,tags,status,createdBy,updatedAt\n");
        for (KnowledgeDocument document : documents) {
            csv.append(csv(document.getTitle())).append(',')
                    .append(csv(document.getContent())).append(',')
                    .append(csv(document.getDomain())).append(',')
                    .append(csv(document.getSource())).append(',')
                    .append(csv(document.getTags())).append(',')
                    .append(document.getStatus()).append(',')
                    .append(csv(document.getCreatedBy())).append(',')
                    .append(document.getUpdatedAt()).append('\n');
        }
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = requireDocument(id);
        if (document.getStatus() == KnowledgeStatus.APPROVED || document.getStatus() == KnowledgeStatus.PENDING_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "待审核或已生效知识不能删除");
        }
        vectorIndex.deleteDocument(id);
        chunkRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
    }

    private KnowledgeDocument saveAndIndex(KnowledgeDocument document, List<ParsedBlock> blocks) {
        KnowledgeDocument saved = documentRepository.save(document);
        vectorIndex.deleteDocument(saved.getId());
        chunkRepository.deleteByDocumentId(saved.getId());
        long indexVersion = saved.getIndexVersion() + 1;
        List<ChunkDraft> drafts = chunkingService.split(saved.getTitle(), blocks);
        List<double[]> vectors = embeddingService.embedAll(drafts.stream().map(ChunkDraft::content).toList());
        List<KnowledgeChunk> chunks = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(saved.getId());
            chunk.setChunkIndex(index);
            chunk.setDocumentVersion(indexVersion);
            chunk.setContent(draft.content());
            chunk.setContentHash(draft.contentHash());
            chunk.setTokenCount(draft.tokenCount());
            chunk.setHeadingPath(blankToNull(draft.headingPath()));
            chunk.setLocator(blankToNull(draft.locator()));
            chunk.setPageNumber(draft.pageNumber());
            chunk.setEmbeddingModel(embeddingService.modelName());
            if (vectorIndex.inlineEmbedding()) {
                chunk.setEmbedding(serialize(vectors.get(index)));
            }
            chunks.add(chunk);
        }
        chunkRepository.saveAllAndFlush(chunks);
        vectorIndex.index(chunks, vectors);
        saved.setChunkCount(drafts.size());
        saved.setIndexVersion(indexVersion);
        return documentRepository.save(saved);
    }

    private List<ParsedBlock> defaultBlocks(String title, String content) {
        return List.of(new ParsedBlock("paragraph", content, 1, title, "manual"));
    }

    private void apply(KnowledgeDocument document, String title, String content, String domain, String source, String tags) {
        document.setTitle(title.trim());
        document.setContent(content.trim());
        document.setDomain(domain.trim());
        document.setSource(source.trim());
        document.setTags(blankToNull(tags));
    }

    private KnowledgeDocument requireDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "知识不存在"));
        return document;
    }

    private String serialize(double[] vector) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.toString();
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (row.stream().anyMatch(value -> !value.isBlank())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        row.add(cell.toString().trim());
        if (row.stream().anyMatch(value -> !value.isBlank())) {
            rows.add(row);
        }
        return rows;
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
