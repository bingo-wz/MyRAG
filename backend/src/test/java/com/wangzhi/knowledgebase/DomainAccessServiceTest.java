package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.config.SecurityClaimProperties;
import com.wangzhi.knowledgebase.security.DomainAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainAccessServiceTest {

    private final DomainAccessService accessService = new DomainAccessService(
            true, new SecurityClaimProperties(null, null, "extended_fields.domains"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operatorCanOnlyAccessClaimedDomains() {
        authenticate(List.of("产品", "客服"), "ROLE_OPERATOR");

        assertThat(accessService.allowed("产品")).isTrue();
        assertThat(accessService.allowed("财务")).isFalse();
        assertThatThrownBy(() -> accessService.checkSearch(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("必须指定知识领域");
    }

    @Test
    void readsDomainsFromConfiguredNestedClaim() {
        authenticate(List.of("产品", "客服"), "ROLE_OPERATOR");

        assertThat(accessService.allowed("客服")).isTrue();
        assertThat(accessService.allowed("财务")).isFalse();
    }

    @Test
    void administratorCanAccessEveryDomain() {
        authenticate(List.of(), "ROLE_ADMIN");

        assertThat(accessService.allowed("财务")).isTrue();
        accessService.checkSearch(null);
    }

    private void authenticate(List<String> domains, String authority) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("tester")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("extended_fields", Map.of("domains", domains))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(authority)), "tester");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
