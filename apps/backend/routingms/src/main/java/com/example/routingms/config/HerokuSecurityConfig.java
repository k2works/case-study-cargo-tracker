package com.example.routingms.config;

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
 * routingms の本番（heroku profile）Spring Security 設定（IT9 A3.1 / US28）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化、全 endpoint を {@code authenticated} に。
 * 認証方式は httpBasic（暫定）、JWT は A3.3 で gatewayms に集約予定。</p>
 *
 * <p>URL ↔ Role マッピング:</p>
 * <ul>
 *   <li>{@code /api/v1/voyages/**} / {@code /api/v1/routes/**}: ROLE_ROUTING / ROLE_ADMIN</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}: permitAll</li>
 * </ul>
 *
 * <p>IT10 A1.1: {@link EnableMethodSecurity} で {@code @PreAuthorize} メソッド認可を
 * 有効化し、URL ルール認可と二段重層の深層防御を確立する。{@code /api/v1/routes/**} は
 * {@link com.example.routingms.interfaces.rest.RouteController} と
 * {@link com.example.routingms.interfaces.rest.RouteDesignRequestController}（{@code /api/v1/routes/design-requests}）
 * の両方をカバー。</p>
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
                        .requestMatchers("/api/v1/voyages/**",
                                "/api/v1/routes/**")
                        .hasAnyRole("ROUTING", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new PreAuthFilter(), AuthorizationFilter.class)
                // httpBasic は明示的に無効化（IT9 H3 / BASIC 突破リスク解消）
                .httpBasic(httpBasic -> httpBasic.disable())
                .build();
    }
}
