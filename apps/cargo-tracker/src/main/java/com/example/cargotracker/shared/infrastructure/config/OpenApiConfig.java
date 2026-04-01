package com.example.cargotracker.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cargoTrackerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Cargo Tracker API")
                .version("v1")
                .description("Cargo Tracker の REST API ドキュメントです。"));
    }
}
