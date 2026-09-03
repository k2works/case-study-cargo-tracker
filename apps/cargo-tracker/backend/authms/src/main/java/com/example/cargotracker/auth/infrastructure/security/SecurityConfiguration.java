package com.example.cargotracker.auth.infrastructure.security;

import com.example.cargotracker.shared.infrastructure.security.JwtSecret;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * authms の入口。認証そのものを提供するので、この経路は認証を要求しない。
 *
 * <p>JWT の検証は Gateway が行う（ADR-0001 決定 4）。ここで二重に検証すると、
 * どちらが正かが曖昧になる。</p>
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtIssuer jwtIssuer(
            @Value("${cargo-tracker.jwt.secret:}") String secret,
            @Value("${cargo-tracker.jwt.validity-minutes:60}") long validityMinutes,
            @Value("${cargo-tracker.production-like:false}") boolean productionLike,
            Clock clock) {
        // 既定値を持たせない。渡し忘れても既知の鍵で起動が成功してしまう。
        return new JwtIssuer(JwtSecret.of(secret, productionLike).value(),
                Duration.ofMinutes(validityMinutes), clock);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
