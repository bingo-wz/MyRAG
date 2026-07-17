package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true",
        "app.demo-data=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.invalid/jwks"
})
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionLogRepository logRepository;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/knowledge").param("domain", "产品"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadClaimedDomain() throws Exception {
        mockMvc.perform(get("/api/knowledge")
                        .param("domain", "产品")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.claim("preferred_username", "viewer")
                                        .claim("domains", List.of("产品")))))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotReadUnclaimedDomain() throws Exception {
        mockMvc.perform(get("/api/knowledge")
                        .param("domain", "财务")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.claim("preferred_username", "viewer")
                                        .claim("domains", List.of("产品")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotModifyAnotherUsersFeedback() throws Exception {
        logRepository.save(questionLog("trace-owner-check", "owner", "产品", false));

        mockMvc.perform(post("/api/qa/trace-owner-check/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false,\"reason\":\"测试\"}")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.claim("preferred_username", "other")
                                        .claim("domains", List.of("产品")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerOnlySeesBadCasesFromClaimedDomains() throws Exception {
        logRepository.save(questionLog("trace-product-case", "owner", "产品", true));
        logRepository.save(questionLog("trace-finance-case", "owner", "财务", true));

        mockMvc.perform(get("/api/analytics/bad-cases")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_REVIEWER"))
                                .jwt(jwt -> jwt.claim("preferred_username", "reviewer")
                                        .claim("domains", List.of("产品")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].traceId").value("trace-product-case"));
    }

    private QuestionLog questionLog(String traceId, String askedBy, String domain, boolean badCase) {
        QuestionLog log = new QuestionLog();
        log.setTraceId(traceId);
        log.setAskedBy(askedBy);
        log.setDomain(domain);
        log.setQuestion("测试问题");
        log.setAnswer("测试回答 [S1]");
        log.setConfidence(0.5);
        log.setLatencyMs(10);
        log.setBadCase(badCase);
        log.setInputTokens(0);
        log.setOutputTokens(0);
        log.setFallback(false);
        return log;
    }
}
