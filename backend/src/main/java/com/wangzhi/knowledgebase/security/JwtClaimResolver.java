package com.wangzhi.knowledgebase.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public final class JwtClaimResolver {

    private JwtClaimResolver() {
    }

    public static Object resolve(Jwt jwt, String claimPath) {
        if (jwt.hasClaim(claimPath)) {
            return jwt.getClaim(claimPath);
        }
        Object current = jwt.getClaims();
        for (String segment : claimPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> values) || !values.containsKey(segment)) {
                return null;
            }
            current = values.get(segment);
        }
        return current;
    }
}
