package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.chat.provider", havingValue = "extractive", matchIfMissing = true)
public class ExtractiveChatService implements ChatService {

    private static final String PROMPT_VERSION = "extractive-v1";
    private final GroundedAnswerBuilder answerBuilder;

    public ExtractiveChatService(GroundedAnswerBuilder answerBuilder) {
        this.answerBuilder = answerBuilder;
    }

    @Override
    public ChatResult generate(String question, List<RetrievedChunk> retrieved) {
        return new ChatResult(answerBuilder.extractive(retrieved), modelName(), PROMPT_VERSION,
                0, 0, true);
    }

    @Override
    public String modelName() {
        return "extractive-fallback";
    }
}
