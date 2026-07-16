package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.repository.KnowledgeChunkRepository;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.vector.store", havingValue = "milvus")
public class MilvusRetrievalBackend implements RetrievalBackend {

    private final MilvusClientV2 client;
    private final EmbeddingService embeddingService;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String collection;

    public MilvusRetrievalBackend(MilvusClientV2 client,
                                  EmbeddingService embeddingService,
                                  KnowledgeChunkRepository chunkRepository,
                                  KnowledgeDocumentRepository documentRepository,
                                  JdbcTemplate jdbcTemplate,
                                  @Value("${app.vector.milvus.collection:myrag_chunks_v1}") String collection) {
        this.client = client;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.collection = collection;
    }

    @Override
    public List<RetrievedChunk> retrieve(String question, String domain, int topK, double minScore) {
        int candidateCount = Math.max(40, topK * 10);
        Map<Long, Double> denseScores = denseCandidates(question, domain, candidateCount);
        Set<Long> candidateIds = new LinkedHashSet<>(denseScores.keySet());
        candidateIds.addAll(lexicalCandidates(question, domain, candidateCount));
        if (candidateIds.isEmpty()) return List.of();

        Map<Long, KnowledgeChunk> chunks = chunkRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Set<Long> documentIds = chunks.values().stream().map(KnowledgeChunk::getDocumentId).collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documents = documentRepository.findByIdIn(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));

        return candidateIds.stream().map(chunkId -> {
                    KnowledgeChunk chunk = chunks.get(chunkId);
                    if (chunk == null) return null;
                    KnowledgeDocument document = documents.get(chunk.getDocumentId());
                    if (document == null) return null;
                    double semantic = Math.max(0, denseScores.getOrDefault(chunkId, 0.0));
                    double lexical = overlap(question, document.getTitle()) * 0.55
                            + overlap(question, chunk.getContent()) * 0.45;
                    double score = semantic * 0.78 + lexical * 0.22;
                    return new RetrievedChunk(chunk.getId(), document.getId(), document.getTitle(), document.getDomain(),
                            chunk.getContent(), chunk.getLocator(), chunk.getPageNumber(), score);
                })
                .filter(item -> item != null && item.score() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private Map<Long, Double> denseCandidates(String question, String domain, int limit) {
        List<Float> vector = new ArrayList<>();
        for (double value : embeddingService.embed(question)) vector.add((float) value);
        StringBuilder filter = new StringBuilder("status == \"APPROVED\"");
        if (domain != null && !domain.isBlank()) {
            filter.append(" && domain == \"").append(escape(domain.trim())).append("\"");
        }
        SearchResp response = client.search(SearchReq.builder()
                .collectionName(collection)
                .annsField("embedding")
                .metricType(IndexParam.MetricType.COSINE)
                .filter(filter.toString())
                .topK(limit)
                .outputFields(List.of("document_id"))
                .searchParams(Map.of("ef", Math.max(64, limit * 2)))
                .data(List.of(new FloatVec(vector)))
                .build());
        Map<Long, Double> scores = new HashMap<>();
        if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) return scores;
        for (SearchResp.SearchResult result : response.getSearchResults().getFirst()) {
            long id = result.getId() instanceof Number number
                    ? number.longValue() : Long.parseLong(result.getId().toString());
            scores.put(id, result.getScore().doubleValue());
        }
        return scores;
    }

    private List<Long> lexicalCandidates(String question, String domain, int limit) {
        String normalizedDomain = domain == null ? "" : domain.trim();
        return jdbcTemplate.query("""
                select c.id
                from knowledge_chunks c
                join knowledge_documents d on d.id = c.document_id
                where d.status = 'APPROVED' and (? = '' or d.domain = ?)
                order by greatest(similarity(c.content, ?), similarity(d.title, ?)) desc
                limit ?
                """, statement -> {
            statement.setString(1, normalizedDomain);
            statement.setString(2, normalizedDomain);
            statement.setString(3, question);
            statement.setString(4, question);
            statement.setInt(5, limit);
        }, (result, row) -> result.getLong(1));
    }

    private double overlap(String query, String candidate) {
        Set<String> queryTerms = terms(query);
        Set<String> candidateTerms = terms(candidate);
        if (queryTerms.isEmpty()) return 0;
        return queryTerms.stream().filter(candidateTerms::contains).count() * 1.0 / queryTerms.size();
    }

    private Set<String> terms(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        int[] points = normalized.codePoints().toArray();
        Set<String> result = new HashSet<>();
        for (int point : points) result.add(new String(Character.toChars(point)));
        for (int index = 0; index < points.length - 1; index++) result.add(new String(points, index, 2));
        return result;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
