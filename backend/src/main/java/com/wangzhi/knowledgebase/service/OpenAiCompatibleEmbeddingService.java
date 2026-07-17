package com.wangzhi.knowledgebase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private final RestClient client;
    private final String model;
    private final int dimensions;
    private final int batchSize;
    private final int maxAttempts;
    private final Timer requestTimer;
    private final Counter failureCounter;

    public OpenAiCompatibleEmbeddingService(
            RestClient.Builder builder,
            @Value("${app.embedding.base-url}") String baseUrl,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.model}") String model,
            @Value("${app.embedding.dimensions:1024}") int dimensions,
            @Value("${app.embedding.batch-size:32}") int batchSize,
            @Value("${app.embedding.max-attempts:3}") int maxAttempts,
            MeterRegistry meterRegistry) {
        RestClient.Builder configured = builder.clone().baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        this.client = configured.build();
        this.model = model;
        this.dimensions = dimensions;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.requestTimer = meterRegistry.timer("myrag.embedding.request.duration", "model", model);
        this.failureCounter = meterRegistry.counter("myrag.embedding.requests", "model", model, "result", "failure");
    }

    @Override
    public double[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<double[]> embedAll(List<String> texts) {
        List<double[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(texts.size(), start + batchSize);
            result.addAll(request(texts.subList(start, end)));
        }
        return result;
    }

    private List<double[]> request(List<String> input) {
        long startedAt = System.nanoTime();
        RestClientException failure = null;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    EmbeddingResponse response = client.post()
                            .uri("/embeddings")
                            .body(new EmbeddingRequest(model, input, "float"))
                            .retrieve()
                            .body(EmbeddingResponse.class);
                    if (response == null || response.data() == null || response.data().size() != input.size()) {
                        throw new IllegalStateException("Embedding 服务返回数量与请求不一致");
                    }
                    return response.data().stream()
                            .sorted(Comparator.comparingInt(EmbeddingData::index))
                            .map(this::toVector)
                            .toList();
                } catch (RestClientException exception) {
                    failure = exception;
                    if (attempt < maxAttempts) {
                        sleep(attempt * 250L);
                    }
                }
            }
            failureCounter.increment();
            throw new IllegalStateException("Embedding 服务连续调用失败", failure);
        } finally {
            requestTimer.record(System.nanoTime() - startedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private double[] toVector(EmbeddingData data) {
        if (data.embedding() == null || data.embedding().size() != dimensions) {
            int actual = data.embedding() == null ? 0 : data.embedding().size();
            throw new IllegalStateException("Embedding 维度不匹配，期望 %d，实际 %d".formatted(dimensions, actual));
        }
        double[] vector = new double[dimensions];
        for (int index = 0; index < dimensions; index++) {
            vector[index] = data.embedding().get(index);
        }
        return vector;
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding 重试等待被中断", exception);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return model;
    }

    private record EmbeddingRequest(String model, List<String> input, String encoding_format) {}

    private record EmbeddingResponse(List<EmbeddingData> data, String model) {}

    private record EmbeddingData(int index, List<Double> embedding) {}
}
