package com.example.billingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * billingms の本番（heroku profile）Spring Security 設定（IT9 A3.1 / US28）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化、業務 endpoint を {@code authenticated} に。
 * 認証方式は httpBasic（暫定）、JWT は A3.3 で gatewayms に集約予定。</p>
 *
 * <p>URL ↔ Role マッピング:</p>
 * <ul>
 *   <li>{@code /api/v1/billing/invoices/**} / {@code /api/v1/billing/circuit-breakers/**}: ROLE_ACCOUNTANT / ROLE_ADMIN</li>
 *   <li>{@code /api/v1/billing/webhooks/stripe}: permitAll（Stripe webhook、HMAC 検証で代替認証）</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}: permitAll</li>
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
                        // Stripe webhook は HMAC 署名検証で代替認証（PaymentGatewayWebhookController）
                        .requestMatchers("/api/v1/billing/webhooks/stripe").permitAll()
                        .requestMatchers("/api/v1/billing/invoices/**",
                                "/api/v1/billing/circuit-breakers/**")
                        .hasAnyRole("ACCOUNTANT", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
