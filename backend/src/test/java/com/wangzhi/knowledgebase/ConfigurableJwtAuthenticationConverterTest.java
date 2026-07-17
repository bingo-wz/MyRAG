package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.config.SecurityClaimProperties;
import com.wangzhi.knowledgebase.security.ConfigurableJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableJwtAuthenticationConverterTest {

    @Test
    void mapsNestedPrincipalAndRoleClaims() {
        ConfigurableJwtAuthenticationConverter converter = new ConfigurableJwtAuthenticationConverter(
                new SecurityClaimProperties("identity.display_name", "authorization.roles", "domains"));
        Jwt jwt = jwt(Map.of(
                "identity", Map.of("display_name", "张三"),
                "authorization", Map.of("roles", List.of(
                        Map.of("code", "KNOWLEDGE_OPERATOR"), "reviewer")),
                "scope", "openid profile"));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("张三");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("SCOPE_openid", "SCOPE_profile", "ROLE_KNOWLEDGE_OPERATOR", "ROLE_REVIEWER");
    }

    @Test
    void prefersExactClaimNameAndFallsBackToSubject() {
        String namespacedClaim = "https://myrag.example.com/roles";
        ConfigurableJwtAuthenticationConverter converter = new ConfigurableJwtAuthenticationConverter(
                new SecurityClaimProperties("missing", namespacedClaim, "domains"));
        Jwt jwt = jwt(Map.of(namespacedClaim, "role_admin"));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("authing-user-1");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("authing-user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
