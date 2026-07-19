package com.wangzhi.knowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = "app.demo-data=false")
@AutoConfigureMockMvc
class LocalApiAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void localApiDoesNotRequireLogin() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceRateDelta").isNumber())
                .andExpect(jsonPath("$.latencyP95Ms").isNumber())
                .andExpect(jsonPath("$.activeImportBatches").isNumber());
    }

    @Test
    void domainOptionsAreAvailableWithoutStaticFrontendConfiguration() throws Exception {
        mockMvc.perform(get("/api/knowledge/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
