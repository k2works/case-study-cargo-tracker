package com.example.cargotracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebJarsResourceTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void thymeleafEngineRegistered() {
        assertThat(context.getBeansOfType(SpringTemplateEngine.class)).isNotEmpty();
    }

    @Test
    void bootstrapWebjarsOnClasspath() {
        assertThat(getClass().getResource("/META-INF/resources/webjars/bootstrap")).isNotNull();
    }

    @Test
    void htmxWebjarsOnClasspath() {
        assertThat(getClass().getResource("/META-INF/resources/webjars/htmx.org")).isNotNull();
    }
}
