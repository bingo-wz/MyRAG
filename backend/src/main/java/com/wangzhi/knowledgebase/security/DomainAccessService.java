package com.wangzhi.knowledgebase.security;

import com.wangzhi.knowledgebase.config.SecurityClaimProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class DomainAccessService {

    private final boolean securityEnabled;
    private final SecurityClaimProperties claims;

    public DomainAccessService(@Value("${app.security.enabled:false}") boolean securityEnabled,
                               SecurityClaimProperties claims) {
        this.securityEnabled = securityEnabled;
        this.claims = claims;
    }

    public void check(String domain) {
        if (!allowed(domain)) {
            throw new AccessDeniedException("当前用户无权访问该知识领域");
        }
    }

    public void checkSearch(String domain) {
        if (!securityEnabled || isAdmin()) {
            return;
        }
        if (domain == null || domain.isBlank()) {
            throw new AccessDeniedException("非管理员查询必须指定知识领域");
        }
        check(domain);
    }

    public boolean allowed(String domain) {
        if (!securityEnabled || isAdmin()) {
            return true;
        }
        if (domain == null || domain.isBlank()) {
            return false;
        }
        Set<String> domains = domains();
        return domains.contains("*") || domains.contains(domain.trim());
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public boolean securityEnabled() {
        return securityEnabled;
    }

    private Set<String> domains() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Set.of();
        }
        Object claim = JwtClaimResolver.resolve(token.getToken(), claims.domains());
        Set<String> result = new LinkedHashSet<>();
        if (claim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank()).forEach(result::add);
        } else if (claim instanceof String value) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    result.add(item.trim());
                }
            }
        }
        return Set.copyOf(result);
    }
}
