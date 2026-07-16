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
import io.milvus.v2.service.vector.request.UpsertReq;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.vector.store", havingValue = "milvus")
public class MilvusChunkVectorIndex implements ChunkVectorIndex {

    private final MilvusClientV2 client;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final Gson gson = new Gson();
    private final String collection;
    private final int dimensions;
    private final int batchSize;

    public MilvusChunkVectorIndex(MilvusClientV2 client,
                                  KnowledgeDocumentRepository documentRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  @Value("${app.vector.milvus.collection:myrag_chunks_v1}") String collection,
                                  @Value("${app.embedding.dimensions:1024}") int dimensions,
                                  @Value("${app.vector.milvus.batch-size:64}") int batchSize) {
        this.client = client;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
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
        KnowledgeDocument document = documentRepository.findById(chunks.getFirst().getDocumentId()).orElseThrow();
        deleteDocument(document.getId());
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<JsonObject> rows = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                KnowledgeChunk chunk = chunks.get(index);
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

    private List<Float> toFloatList(double[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (double value : vector) values.add((float) value);
        return values;
    }
}
