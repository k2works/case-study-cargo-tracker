package com.example.gatewayms.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public PublicPathMatcher publicPathMatcher(GatewaySecurityProperties properties) {
        return new PublicPathMatcher(properties.toPublicPaths());
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            PublicPathMatcher publicPathMatcher, GatewaySecurityProperties properties) {
        return new JwtAuthenticationFilter(publicPathMatcher, properties.jwt().secret());
    }
}
