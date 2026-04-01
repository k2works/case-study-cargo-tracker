package com.example.cargotracker.shared.infrastructure.config;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean openApiEnabled;

    public SecurityConfig(@Value("${app.openapi.enabled:false}") boolean openApiEnabled) {
        this.openApiEnabled = openApiEnabled;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        RequestMatcher h2ConsoleMatcher = new OrRequestMatcher(
                RegexRequestMatcher.regexMatcher("^/h2-console/?$"),
                PathRequest.toH2Console()
        );
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(h2ConsoleMatcher))
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(h2ConsoleMatcher).permitAll();
                auth.requestMatchers(
                        "/login",
                        "/webjars/**",
                        "/css/**",
                        "/js/**"
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
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.pathPattern("/api/**")
                )
                .defaultAccessDeniedHandlerFor(
                    (request, response, accessDeniedException) ->
                        response.sendError(HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase()),
                    PathPatternRequestMatcher.pathPattern("/api/**")
                )
            )
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
