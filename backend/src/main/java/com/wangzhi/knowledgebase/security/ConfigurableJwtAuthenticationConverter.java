package com.wangzhi.knowledgebase.security;

import com.wangzhi.knowledgebase.config.SecurityClaimProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ConfigurableJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecurityClaimProperties claims;
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    public ConfigurableJwtAuthenticationConverter(SecurityClaimProperties claims) {
        this.claims = claims;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
        authorities.addAll(roleAuthorities(JwtClaimResolver.resolve(jwt, claims.roles())));
        return new JwtAuthenticationToken(jwt, authorities, principal(jwt));
    }

    private String principal(Jwt jwt) {
        Object value = JwtClaimResolver.resolve(jwt, claims.principal());
        if (value != null && !String.valueOf(value).isBlank()) {
            return String.valueOf(value).trim();
        }
        return jwt.getSubject() == null || jwt.getSubject().isBlank() ? "unknown" : jwt.getSubject();
    }

    private List<GrantedAuthority> roleAuthorities(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .map(this::roleValue)
                    .filter(value -> !value.isBlank())
                    .map(this::role)
                    .toList();
        }
        if (claim instanceof String value && !value.isBlank()) {
            return Arrays.stream(value.split("[,\\s]+"))
                    .filter(item -> !item.isBlank())
                    .map(this::role)
                    .toList();
        }
        return List.of();
    }

    private String roleValue(Object value) {
        if (value instanceof Map<?, ?> role) {
            Object code = role.get("code");
            if (code == null) {
                code = role.get("name");
            }
            return code == null ? "" : String.valueOf(code).trim();
        }
        return value == null ? "" : String.valueOf(value).trim();
    }

    private GrantedAuthority role(String value) {
        String normalized = value.regionMatches(true, 0, "ROLE_", 0, 5) ? value : "ROLE_" + value;
        return new SimpleGrantedAuthority(normalized.toUpperCase(Locale.ROOT));
    }
}
