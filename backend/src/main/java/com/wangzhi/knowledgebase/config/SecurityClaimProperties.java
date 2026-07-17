package com.wangzhi.knowledgebase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.claims")
public record SecurityClaimProperties(String principal, String roles, String domains) {

    public SecurityClaimProperties {
        principal = valueOrDefault(principal, "preferred_username");
        roles = valueOrDefault(roles, "roles");
        domains = valueOrDefault(domains, "domains");
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
