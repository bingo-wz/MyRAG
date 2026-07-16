package com.wangzhi.knowledgebase.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.vector.store", havingValue = "milvus")
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(@Value("${app.vector.milvus.uri}") String uri,
                                       @Value("${app.vector.milvus.token:}") String token) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(10_000)
                .rpcDeadlineMs(30_000);
        if (token != null && !token.isBlank()) builder.token(token.trim());
        return new MilvusClientV2(builder.build());
    }
}
