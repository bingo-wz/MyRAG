package com.wangzhi.knowledgebase.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public KafkaAdmin.NewTopics importTopics(
            @Value("${app.kafka.import-topic:myrag.import.requested.v1}") String importTopic,
            @Value("${app.kafka.partitions:3}") int partitions,
            @Value("${app.kafka.replication-factor:1}") short replicationFactor) {
        return new KafkaAdmin.NewTopics(new NewTopic(importTopic, partitions, replicationFactor));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1_000L, 2L));
    }
}
