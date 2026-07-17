package com.wangzhi.knowledgebase.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wangzhi.knowledgebase.domain.KnowledgeChunk;
import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.repository.KnowledgeChunkRepository;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@ConditionalOnProperty(name = "app.vector.store", havingValue = "milvus")
public class MilvusChunkVectorIndex implements ChunkVectorIndex {

    private final MilvusClientV2 client;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final Gson gson = new Gson();
    private final String collection;
    private final int dimensions;
    private final int batchSize;

    public MilvusChunkVectorIndex(MilvusClientV2 client,
                                  KnowledgeDocumentRepository documentRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  EmbeddingService embeddingService,
                                  @Value("${app.vector.milvus.collection:myrag_chunks_v1}") String collection,
                                  @Value("${app.embedding.dimensions:1024}") int dimensions,
                                  @Value("${app.vector.milvus.batch-size:64}") int batchSize) {
        this.client = client;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.collection = collection;
        this.dimensions = dimensions;
        this.batchSize = Math.max(1, batchSize);
    }

    @PostConstruct
    void ensureCollection() {
        if (client.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) return;
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder().fieldName("chunk_id").dataType(DataType.Int64)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("document_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("domain").dataType(DataType.VarChar).maxLength(80).build());
        schema.addField(AddFieldReq.builder().fieldName("status").dataType(DataType.VarChar).maxLength(32).build());
        schema.addField(AddFieldReq.builder().fieldName("content_hash").dataType(DataType.VarChar).maxLength(64).build());
        schema.addField(AddFieldReq.builder().fieldName("embedding").dataType(DataType.FloatVector)
                .dimension(dimensions).build());

        List<IndexParam> indexes = List.of(
                IndexParam.builder().fieldName("embedding").indexName("embedding_hnsw")
                        .indexType(IndexParam.IndexType.HNSW).metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("M", 16, "efConstruction", 128, "mmap.enabled", true)).build(),
                IndexParam.builder().fieldName("domain").indexName("domain_index")
                        .indexType(IndexParam.IndexType.INVERTED).build(),
                IndexParam.builder().fieldName("status").indexName("status_index")
                        .indexType(IndexParam.IndexType.INVERTED).build()
        );
        client.createCollection(CreateCollectionReq.builder().collectionName(collection)
                .collectionSchema(schema).indexParams(indexes).build());
    }

    @Override
    public boolean inlineEmbedding() {
        return false;
    }

    @Override
    public void index(List<KnowledgeChunk> chunks, List<double[]> vectors) {
        if (chunks.isEmpty()) return;
        if (chunks.size() != vectors.size()) throw new IllegalArgumentException("Chunk 与向量数量不一致");
        chunks.stream().map(KnowledgeChunk::getDocumentId).distinct().forEach(this::deleteDocument);
        upsertRows(chunks, vectors);
    }

    private void upsertRows(List<KnowledgeChunk> chunks, List<double[]> vectors) {
        Set<Long> documentIds = chunks.stream().map(KnowledgeChunk::getDocumentId).collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documents = documentRepository.findByIdIn(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<JsonObject> rows = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                KnowledgeChunk chunk = chunks.get(index);
                KnowledgeDocument document = documents.get(chunk.getDocumentId());
                if (document == null) throw new IllegalStateException("Chunk 对应知识不存在：" + chunk.getDocumentId());
                JsonObject row = new JsonObject();
                row.addProperty("chunk_id", chunk.getId());
                row.addProperty("document_id", chunk.getDocumentId());
                row.addProperty("domain", document.getDomain());
                row.addProperty("status", document.getStatus().name());
                row.addProperty("content_hash", chunk.getContentHash());
                row.add("embedding", gson.toJsonTree(toFloatList(vectors.get(index))));
                rows.add(row);
            }
            client.upsert(UpsertReq.builder().collectionName(collection).data(rows).build());
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        client.delete(DeleteReq.builder().collectionName(collection)
                .filter("document_id == " + documentId).build());
    }

    @Override
    public void updateDocumentStatus(Long documentId, KnowledgeStatus status) {
        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdIn(List.of(documentId));
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<JsonObject> rows = new ArrayList<>(end - start);
            for (KnowledgeChunk chunk : chunks.subList(start, end)) {
                JsonObject row = new JsonObject();
                row.addProperty("chunk_id", chunk.getId());
                row.addProperty("status", status.name());
                rows.add(row);
            }
            client.upsert(UpsertReq.builder().collectionName(collection).data(rows).partialUpdate(true).build());
        }
    }

    @Override
    public IndexReconciliationReport reconcile() {
        long databaseChunks = chunkRepository.count();
        long repairedRows = 0;
        long repairedStatuses = 0;
        int pageNumber = 0;
        Page<KnowledgeChunk> page;
        do {
            page = chunkRepository.findAll(PageRequest.of(pageNumber++, batchSize, Sort.by("id")));
            List<KnowledgeChunk> chunks = page.getContent();
            if (!chunks.isEmpty()) {
                Map<Long, Map<String, Object>> remote = getRows(chunks);
                Set<Long> documentIds = chunks.stream().map(KnowledgeChunk::getDocumentId).collect(Collectors.toSet());
                Map<Long, KnowledgeDocument> documents = documentRepository.findByIdIn(documentIds).stream()
                        .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
                List<KnowledgeChunk> repair = new ArrayList<>();
                List<KnowledgeChunk> statusRepair = new ArrayList<>();
                for (KnowledgeChunk chunk : chunks) {
                    Map<String, Object> row = remote.get(chunk.getId());
                    if (row == null || !chunk.getContentHash().equals(String.valueOf(row.get("content_hash")))) {
                        repair.add(chunk);
                        continue;
                    }
                    KnowledgeDocument document = documents.get(chunk.getDocumentId());
                    if (document != null && !document.getStatus().name().equals(String.valueOf(row.get("status")))) {
                        statusRepair.add(chunk);
                    }
                }
                if (!repair.isEmpty()) {
                    List<double[]> vectors = embeddingService.embedAll(repair.stream().map(KnowledgeChunk::getContent).toList());
                    upsertRows(repair, vectors);
                    repairedRows += repair.size();
                }
                if (!statusRepair.isEmpty()) {
                    partialStatusUpdate(statusRepair, documents);
                    repairedStatuses += statusRepair.size();
                }
            }
        } while (page.hasNext());

        long vectorRows = 0;
        long deletedOrphans = 0;
        QueryIterator iterator = client.queryIterator(QueryIteratorReq.builder()
                .collectionName(collection).expr("chunk_id >= 0")
                .outputFields(List.of("chunk_id")).batchSize(batchSize).build());
        try {
            while (true) {
                List<QueryResultsWrapper.RowRecord> rows = iterator.next();
                if (rows == null || rows.isEmpty()) break;
                vectorRows += rows.size();
                List<Long> ids = rows.stream().map(row -> number(row.getFieldValues().get("chunk_id"))).toList();
                Set<Long> existing = chunkRepository.findAllById(ids).stream()
                        .map(KnowledgeChunk::getId).collect(Collectors.toSet());
                List<Long> orphans = ids.stream().filter(id -> !existing.contains(id)).toList();
                if (!orphans.isEmpty()) {
                    client.delete(DeleteReq.builder().collectionName(collection)
                            .filter("chunk_id in [" + orphans.stream().map(String::valueOf)
                                    .collect(Collectors.joining(",")) + "]").build());
                    deletedOrphans += orphans.size();
                }
            }
        } finally {
            iterator.close();
        }
        return new IndexReconciliationReport(true, databaseChunks, vectorRows, repairedRows,
                repairedStatuses, deletedOrphans, java.time.LocalDateTime.now(), "对账完成");
    }

    private Map<Long, Map<String, Object>> getRows(List<KnowledgeChunk> chunks) {
        GetResp response = client.get(GetReq.builder().collectionName(collection)
                .ids(chunks.stream().map(chunk -> (Object) chunk.getId()).toList())
                .outputFields(List.of("chunk_id", "content_hash", "status")).build());
        if (response.getGetResults() == null) return Map.of();
        Map<Long, Map<String, Object>> result = new HashMap<>();
        response.getGetResults().forEach(row -> result.put(number(row.getEntity().get("chunk_id")), row.getEntity()));
        return result;
    }

    private void partialStatusUpdate(List<KnowledgeChunk> chunks, Map<Long, KnowledgeDocument> documents) {
        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) continue;
            JsonObject row = new JsonObject();
            row.addProperty("chunk_id", chunk.getId());
            row.addProperty("status", document.getStatus().name());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            client.upsert(UpsertReq.builder().collectionName(collection).data(rows).partialUpdate(true).build());
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private List<Float> toFloatList(double[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (double value : vector) values.add((float) value);
        return values;
    }
}
