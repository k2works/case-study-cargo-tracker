package com.example.bookingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * bookingms の本番（heroku profile）Spring Security 設定（IT9 A3.1 / US28）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化し、全 endpoint を
 * {@code anyRequest().authenticated()} に切り替える。ロールベース認可は
 * URL マッチで設定し、IT10 A1.1 で {@link EnableMethodSecurity} + {@code @PreAuthorize} を
 * 重ねて深層防御を確立する。</p>
 *
 * <p>認証方式は httpBasic（暫定）。JWT 検証チェーンは A3.3 で gatewayms に集約し、
 * 各 ms はヘッダから user / role を受け取る前提に移行する。</p>
 *
 * <p>URL ↔ Role マッピング:</p>
 * <ul>
 *   <li>{@code /api/v1/shippers/**} / {@code /api/v1/bookings/**} / {@code /api/v1/quotes/**}: ROLE_SALES / ROLE_ADMIN</li>
 *   <li>{@code /api/v1/routing/**}: ROLE_ROUTING / ROLE_ADMIN（経路設計担当者）</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}: permitAll（Heroku health check 用）</li>
 * </ul>
 *
 * <p>IT10 A1.1 で {@code /api/v1/quotations/**} → {@code /api/v1/quotes/**} に修正
 * （実 Controller URL と整合）。</p>
 *
 * <p>IT10 A1.4 (IT9 H3): {@link PreAuthFilter} を {@link AuthorizationFilter} の前段に挿入し、
 * gatewayms 由来の {@code X-Forwarded-User} / {@code X-Forwarded-Role} ヘッダを
 * {@code Authentication} に変換する。同時に {@code httpBasic} を無効化することで、
 * gatewayms をバイパスした各 ms への直接アクセスを 401 で拒否し、BASIC 認証突破リスクを解消する。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("heroku")
public class HerokuSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/shippers/**",
                                "/api/v1/bookings/**",
                                "/api/v1/quotes/**")
                        .hasAnyRole("SALES", "ADMIN")
                        .requestMatchers("/api/v1/routing/**")
                        .hasAnyRole("ROUTING", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new PreAuthFilter(), AuthorizationFilter.class)
                // httpBasic は明示的に無効化（IT9 H3 / BASIC 突破リスク解消）
                .httpBasic(httpBasic -> httpBasic.disable())
                .build();
    }
}
