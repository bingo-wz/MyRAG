package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.domain.OutboxEvent;
import com.wangzhi.knowledgebase.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaOutboxImportDispatch implements ImportDispatch {

    private final OutboxEventRepository repository;
    private final String topic;

    public KafkaOutboxImportDispatch(OutboxEventRepository repository,
                                     @Value("${app.kafka.import-topic:myrag.import.requested.v1}") String topic) {
        this.repository = repository;
        this.topic = topic;
    }

    @Override
    public void dispatch(String batchId) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTopic(topic);
        event.setEventKey(batchId);
        event.setEventType("ImportRequestedV1");
        event.setPayload(batchId);
        repository.save(event);
    }
}
