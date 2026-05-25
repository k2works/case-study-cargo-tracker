package com.example.authms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-h2")
class AuthMsLocalH2SmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
