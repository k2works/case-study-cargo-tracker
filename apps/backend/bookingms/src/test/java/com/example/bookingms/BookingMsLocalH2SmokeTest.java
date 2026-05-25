package com.example.bookingms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        }
)
@ActiveProfiles("local-h2")
class BookingMsLocalH2SmokeTest {

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
    }
}
