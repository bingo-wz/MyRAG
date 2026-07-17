package com.wangzhi.knowledgebase.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityIdentity {

    private final boolean securityEnabled;

    public SecurityIdentity(@Value("${app.security.enabled:false}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public String currentOr(String fallback) {
        if (!securityEnabled) {
            return fallback;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }

    public String current() {
        return currentOr("anonymous");
    }
}
