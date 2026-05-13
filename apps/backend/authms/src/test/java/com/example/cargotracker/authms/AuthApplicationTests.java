package com.example.cargotracker.authms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("AuthApplication コンテキスト起動確認")
class AuthApplicationTests {

    @Test
    @DisplayName("Spring コンテキストが正常に起動する")
    void contextLoads() {
        // Spring コンテキストが起動できれば成功
    }
}
