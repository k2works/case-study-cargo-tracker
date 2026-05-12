package com.example.cargotracker.bookingms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * BookingApplication の Spring コンテキスト起動確認テスト。
 *
 * <p>Phase 0 の Walking Skeleton。Bean 解決のみ確認し、外部リソースには接続しない。
 */
@SpringBootTest
@ActiveProfiles("local-h2")
class BookingApplicationTests {

    @Test
    void contextLoads() {
        // Spring コンテキストが起動できれば成功
    }
}
