package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.KnowledgeDocument;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.repository.KnowledgeChunkRepository;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.vector.store", havingValue = "inline", matchIfMissing = true)
public class InlineRetrievalBackend implements RetrievalBackend {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    public InlineRetrievalBackend(KnowledgeDocumentRepository documentRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(String question, String domain, int topK, double minScore) {
        List<KnowledgeDocument> approved = documentRepository.findByStatus(KnowledgeStatus.APPROVED).stream()
                .filter(document -> domain == null || domain.isBlank() || document.getDomain().equals(domain))
                .toList();
        if (approved.isEmpty()) return List.of();
        Map<Long, KnowledgeDocument> documents = approved.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        double[] queryVector = embeddingService.embed(question);
        Set<String> intentTerms = detectIntent(question);
        return chunkRepository.findByDocumentIdIn(documents.keySet()).stream()
                .filter(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isBlank())
                .map(chunk -> {
                    KnowledgeDocument document = documents.get(chunk.getDocumentId());
                    double semantic = cosine(queryVector, deserialize(chunk.getEmbedding()));
                    double lexical = overlap(question, document.getTitle()) * 0.65
                            + overlap(question, chunk.getContent()) * 0.35;
                    double intent = intentScore(intentTerms, document.getTitle() + " " + chunk.getContent());
                    double score = intentTerms.isEmpty()
                            ? semantic * 0.70 + lexical * 0.30
                            : (intent == 0 ? 0 : semantic * 0.35 + lexical * 0.20 + intent * 0.45);
                    return new RetrievedChunk(chunk.getId(), document.getId(), document.getTitle(),
                            document.getDomain(), chunk.getContent(), chunk.getLocator(), chunk.getPageNumber(), score);
                })
                .filter(item -> item.score() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private double[] deserialize(String serialized) {
        String[] values = serialized.split(",");
        double[] vector = new double[values.length];
        for (int index = 0; index < values.length; index++) vector[index] = Double.parseDouble(values[index]);
        return vector;
    }

    private double cosine(double[] left, double[] right) {
        double value = 0;
        for (int index = 0; index < Math.min(left.length, right.length); index++) value += left[index] * right[index];
        return value;
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

    private Set<String> detectIntent(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (text.matches(".*(退货|换货|退款|能退|可以退|退吗).*")) return Set.of("退货", "换货", "无理由", "签收");
        if (text.matches(".*(保修|维修|修理|故障).*")) return Set.of("保修", "维修", "服务中心", "网点");
        if (text.contains("积分")) return Set.of("积分", "抵扣", "兑换");
        if (text.contains("优惠券") || text.contains("券")) return Set.of("优惠券", "满减券", "平台券", "商品券");
        if (text.matches(".*(配送|物流|送到|到货|几天到).*")) return Set.of("配送", "物流", "送达", "出库");
        if (text.matches(".*(以旧换新|回收|旧机).*")) return Set.of("以旧换新", "回收", "估价", "验机");
        if (text.matches(".*(电池|充电|续航).*")) return Set.of("电池", "充电", "续航", "高温");
        if (text.matches(".*(注销|销户).*")) return Set.of("注销", "账号", "个人数据", "身份校验");
        if (text.matches(".*(发票|税号|开票).*")) return Set.of("发票", "税号", "抬头", "邮箱");
        if (text.matches(".*(预约|服务网点|服务门店).*")) return Set.of("预约", "服务中心", "门店", "网点");
        return Set.of();
    }

    private double intentScore(Set<String> intentTerms, String candidate) {
        if (intentTerms.isEmpty()) return 0;
        return intentTerms.stream().filter(candidate::contains).count() * 1.0 / intentTerms.size();
    }
}
