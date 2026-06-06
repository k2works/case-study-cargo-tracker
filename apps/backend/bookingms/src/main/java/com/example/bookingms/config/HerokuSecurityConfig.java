package com.example.bookingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * bookingms の本番（heroku profile）Spring Security 設定（IT9 A3.1 / US28）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化し、全 endpoint を
 * {@code anyRequest().authenticated()} に切り替える。ロールベース認可は
 * URL マッチで設定する（{@code @PreAuthorize} は A3.2 / IT10 で個別 Controller に追加）。</p>
 *
 * <p>認証方式は httpBasic（暫定）。JWT 検証チェーンは A3.3 で gatewayms に集約し、
 * 各 ms はヘッダから user / role を受け取る前提に移行する。</p>
 *
 * <p>URL ↔ Role マッピング:</p>
 * <ul>
 *   <li>{@code /api/v1/shippers/**} / {@code /api/v1/bookings/**} / {@code /api/v1/quotations/**}: ROLE_SALES / ROLE_ADMIN</li>
 *   <li>{@code /api/v1/routing/**}: ROLE_ROUTING / ROLE_ADMIN（経路設計担当者）</li>
 *   <li>{@code /actuator/health}: permitAll（Heroku health check 用）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
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
                                "/api/v1/quotations/**")
                        .hasAnyRole("SALES", "ADMIN")
                        .requestMatchers("/api/v1/routing/**")
                        .hasAnyRole("ROUTING", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
