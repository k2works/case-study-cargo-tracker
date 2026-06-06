package com.example.trackingms.config;

import com.example.trackingms.domain.services.TrackingTokenService;
import com.example.trackingms.interfaces.rest.PublicTrackingTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * trackingms の本番（heroku profile）Spring Security 設定（IT9 A3.1 / US28）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化。公開照会 chain（IT6 PublicTrackingTokenFilter）は
 * 既存パターンを維持し、業務 endpoint を {@code authenticated} に切り替える。</p>
 *
 * <p>URL ↔ Role マッピング:</p>
 * <ul>
 *   <li>{@code /api/v1/public/tracking/**}: 時限署名トークン検証（PublicTrackingTokenFilter）</li>
 *   <li>{@code /api/v1/tracking/**}: ROLE_TRACKER / ROLE_ADMIN</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}: permitAll</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@Profile("heroku")
public class HerokuSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain publicTrackingFilterChain(HttpSecurity http,
                                                         TrackingTokenService tokenService) throws Exception {
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
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/tracking/**")
                        .hasAnyRole("TRACKER", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
