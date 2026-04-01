package com.example.cargotracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThymeleafConfigurationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private Map<String, org.springframework.security.web.SecurityFilterChain> filterChains;

    @Test
    void securityFilterChainConfigured() {
        assertThat(filterChains).isNotEmpty();
    }

    @Test
    void portIsPositive() {
        assertThat(port).isPositive();
    }
}
