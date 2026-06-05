package com.example.routingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * routingms の Spring Security 設定（IT8 H2 持ち越し T1.4 全サービス Spring Security 統一）。
 *
 * <p>IT7 までは Spring Security 未導入で全 endpoint が無認証で動作していた。本 IT8 では
 * 全 backend ms に SecurityFilterChain を導入して基盤を統一する。互換性維持のため現状は
 * {@code permitAll} のみ。IT9 で各 endpoint に {@code authenticated()} + {@code @PreAuthorize}
 * を順次付与する。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
