package com.example.cargotracker.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean openApiEnabled;
    private final boolean testResetEnabled;

    public SecurityConfig(
            @Value("${app.openapi.enabled:false}") boolean openApiEnabled,
            @Value("${app.test-reset.enabled:false}") boolean testResetEnabled) {
        this.openApiEnabled = openApiEnabled;
        this.testResetEnabled = testResetEnabled;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> {
                    if (testResetEnabled) {
                        auth.requestMatchers("/api/test/reset").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .csrf(csrf -> {
                    if (testResetEnabled) {
                        csrf.ignoringRequestMatchers("/api/test/reset");
                    }
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase()))
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) {
        http
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/login",
                            "/webjars/**",
                            "/css/**",
                            "/js/**",
                            "/actuator/health"
                    ).permitAll();
                    if (openApiEnabled) {
                        auth.requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );
        return http.build();
    }
}
