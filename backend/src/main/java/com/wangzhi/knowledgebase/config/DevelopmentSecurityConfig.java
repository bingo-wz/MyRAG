package com.wangzhi.knowledgebase.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnExpression("'${app.security.enabled:false}' == 'false' and '${app.runtime.api-enabled:true}' == 'true'")
public class DevelopmentSecurityConfig {

    @Bean
    SecurityFilterChain developmentSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 开发 API 没有 Cookie 身份，保留默认 CSRF 机制并仅忽略 API 路径。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
