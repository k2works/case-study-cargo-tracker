package com.example.gatewayms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-h2")
class GatewayMsLocalH2SmokeTest {

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
    }
}
