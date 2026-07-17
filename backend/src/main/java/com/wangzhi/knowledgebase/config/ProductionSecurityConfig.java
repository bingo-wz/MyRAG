package com.wangzhi.knowledgebase.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
@ConditionalOnExpression("'${app.security.enabled:false}' == 'true' and '${app.runtime.api-enabled:true}' == 'true'")
public class ProductionSecurityConfig {

    @Bean
    SecurityFilterChain productionSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // API 只接受 Bearer Token，不使用浏览器 Cookie 或服务端 Session。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.addAll(roleAuthorities(jwt.getClaim("roles")));
            return authorities;
        });
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private List<GrantedAuthority> roleAuthorities(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).map(this::role).toList();
        }
        if (claim instanceof String value && !value.isBlank()) {
            return Arrays.stream(value.split("[, ]+")).map(this::role).toList();
        }
        return List.of();
    }

    private GrantedAuthority role(String value) {
        String normalized = value.startsWith("ROLE_") ? value : "ROLE_" + value;
        return new SimpleGrantedAuthority(normalized.toUpperCase());
    }
}
