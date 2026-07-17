package com.wangzhi.knowledgebase.service;

import java.util.List;

public interface ChatService {

    ChatResult generate(String question, List<RetrievedChunk> retrieved);

    default boolean generative() {
        return false;
    }

    String modelName();
}
