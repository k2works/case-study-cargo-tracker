package com.example.gatewayms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
@DisplayName("GatewayApplication")
class GatewayApplicationTest {

    @Test
    @DisplayName("アプリケーションコンテキストが起動する")
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("gatewayms");
    }
}
