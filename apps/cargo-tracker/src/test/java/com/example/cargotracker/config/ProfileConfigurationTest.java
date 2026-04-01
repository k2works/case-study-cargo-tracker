package com.example.cargotracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileConfigurationTest {

    @Nested
    @SpringBootTest(properties = "app.seed.enabled=true")
    class DefaultProfile {

        @Autowired
        private Environment env;

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private ShipperRepository shipperRepository;

        @Autowired
        private BookingRepository bookingRepository;

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

        @Test
        void openApiEnabled() {
            assertThat(env.getProperty("app.openapi.enabled")).isEqualTo("true");
            assertThat(applicationContext.getBeansOfType(OpenAPI.class)).hasSize(1);
        }

        @Test
        void seedDataLoaded() {
            assertThat(shipperRepository.findAll()).isNotEmpty();
            assertThat(bookingRepository.findAll()).isNotEmpty();
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

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void postgresqlDriverConfigured() {
            String driver = env.getProperty("spring.datasource.driver-class-name");
            assertThat(driver).isEqualTo("org.postgresql.Driver");
        }

        @Test
        void openApiDisabled() {
            assertThat(env.getProperty("app.openapi.enabled")).isEqualTo("false");
            assertThat(applicationContext.getBeansOfType(OpenAPI.class)).isEmpty();
        }
    }
}
