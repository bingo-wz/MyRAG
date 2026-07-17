package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.service.ChatResult;
import com.wangzhi.knowledgebase.service.GroundedAnswerBuilder;
import com.wangzhi.knowledgebase.service.OpenAiCompatibleChatService;
import com.wangzhi.knowledgebase.service.RetrievedChunk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatServiceTest {

    @Test
    void shouldAcceptGroundedAnswerAndRecordUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"系统使用 Kafka 处理导入事件。[S1]"}}],
                         "usage":{"prompt_tokens":120,"completion_tokens":18,"total_tokens":138}}
                        """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleChatService service = service(builder);

        ChatResult result = service.generate("如何导入？", List.of(chunk()));

        assertThat(result.answer()).contains("[S1]");
        assertThat(result.fallback()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(18);
        server.verify();
    }

    @Test
    void shouldFallbackWhenModelReturnsInvalidCitation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://chat.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"这是没有依据的回答。[S9]"}}]}
                        """, MediaType.APPLICATION_JSON));

        ChatResult result = service(builder).generate("如何导入？", List.of(chunk()));

        assertThat(result.fallback()).isTrue();
        assertThat(result.answer()).contains("[S1]");
        server.verify();
    }

    private OpenAiCompatibleChatService service(RestClient.Builder builder) {
        return new OpenAiCompatibleChatService(builder, new GroundedAnswerBuilder(),
                "http://chat.test/v1", "", "test-chat", 256, 1, new SimpleMeterRegistry());
    }

    private RetrievedChunk chunk() {
        return new RetrievedChunk(1L, 2L, "导入说明", "研发", "系统使用 Kafka 处理导入事件。",
                "page:1#block:1", 1, 0.9);
    }
}
