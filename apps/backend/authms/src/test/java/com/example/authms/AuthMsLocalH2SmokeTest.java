package com.example.authms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-h2")
class AuthMsLocalH2SmokeTest {

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
    }
}
