package com.example.cargotracker.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileConfigurationTest {

    @Nested
    @SpringBootTest
    class DefaultProfile {

        @Autowired
        private Environment env;

        @Test
        void h2DatabaseConfigured() {
            String url = env.getProperty("spring.datasource.url");
            assertThat(url).contains("h2:mem");
        }

        @Test
        void flywayEnabled() {
            String enabled = env.getProperty("spring.flyway.enabled");
            assertThat(enabled).isEqualTo("true");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:h2:mem:test_product;MODE=PostgreSQL"
    })
    @ActiveProfiles("product")
    class ProductProfile {

        @Autowired
        private Environment env;

        @Test
        void postgresqlDriverConfigured() {
            String driver = env.getProperty("spring.datasource.driver-class-name");
            assertThat(driver).isEqualTo("org.postgresql.Driver");
        }
    }
}
