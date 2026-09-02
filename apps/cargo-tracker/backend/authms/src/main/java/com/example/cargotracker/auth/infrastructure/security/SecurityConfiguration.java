package com.example.cargotracker.auth.infrastructure.security;

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
            @Value("${cargo-tracker.jwt.secret:cargo-tracker-development-secret-key-32bytes!}")
            String secret,
            @Value("${cargo-tracker.jwt.validity-minutes:60}") long validityMinutes,
            Clock clock) {
        return new JwtIssuer(secret, Duration.ofMinutes(validityMinutes), clock);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
