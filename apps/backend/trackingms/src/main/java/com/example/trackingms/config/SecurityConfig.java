package com.example.trackingms.config;

import com.example.trackingms.domain.services.TrackingTokenService;
import com.example.trackingms.interfaces.rest.PublicTrackingTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * trackingms の Spring Security 設定（IT8 H2 持ち越し T1.5、ADR-0013 連動）。
 *
 * <p>2 つの {@link SecurityFilterChain} で構成する:</p>
 *
 * <ol>
 *   <li><strong>publicTrackingFilterChain</strong>: {@code /api/v1/public/tracking/**} に
 *       {@link PublicTrackingTokenFilter} を {@link AuthorizationFilter} の前に挿入し、
 *       JWT 検証失敗時に 403 Problem Detail を返す。Spring Security の標準
 *       認証 / 認可はバイパス（フィルタが既に検証済み）。</li>
 *   <li><strong>defaultFilterChain</strong>: その他のすべての URL を {@code permitAll} で許可
 *       （IT7 までの動作を保持）。IT9 T1.4「全サービス Spring Security 統一」で
 *       {@code authenticated()} + JWT 検証フィルタへ移行予定。</li>
 * </ol>
 *
 * <p>従来の {@link org.springframework.boot.web.servlet.FilterRegistrationBean} 経由の
 * 登録（{@code PublicTrackingTokenFilter.FilterRegistration}）を SecurityFilterChain
 * 経由へ統合し、標準的な Spring Security の構成へ移行する。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain publicTrackingFilterChain(HttpSecurity http,
                                                         TrackingTokenService tokenService) throws Exception {
        // AntPathRequestMatcher を明示的に使用して MvcRequestMatcher（HandlerMappingIntrospector 依存）を回避
        // → web-application-type=none の SmokeTest / Kafka 統合テストでも起動できる
        return http
                .securityMatcher(new AntPathRequestMatcher("/api/v1/public/tracking/**"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new PublicTrackingTokenFilter(tokenService), AuthorizationFilter.class)
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        // IT9 T1.4「全サービス Spring Security 統一」で authenticated() + JWT 検証フィルタへ移行予定。
        // 現状は IT7 までの「認証なし」動作を維持し、Spring Security の挿入を public 照会に限定する。
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
