package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.service.OpenAiCompatibleEmbeddingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEmbeddingServiceTest {

    @Test
    void shouldBatchAndRestoreVectorsByResponseIndex() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://embedding.test/v1/embeddings"))
                .andExpect(content().json("""
                        {"model":"bge-m3","input":["问题一","问题二"],"encoding_format":"float"}
                        """))
                .andRespond(withSuccess("""
                        {"model":"bge-m3","data":[
                          {"index":1,"embedding":[0.0,1.0,0.0]},
                          {"index":0,"embedding":[1.0,0.0,0.0]}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(builder,
                "http://embedding.test/v1", "", "bge-m3", 3, 32, 1, new SimpleMeterRegistry());

        List<double[]> vectors = service.embedAll(List.of("问题一", "问题二"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0, 0.0, 0.0);
        assertThat(vectors.get(1)).containsExactly(0.0, 1.0, 0.0);
        server.verify();
    }
}
