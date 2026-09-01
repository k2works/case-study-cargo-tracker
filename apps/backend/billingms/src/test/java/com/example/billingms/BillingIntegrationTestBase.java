package com.example.billingms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 実 DB を使う検査の土台。
 *
 * <p><strong>DB は 1 つを共有し、止めない。</strong>テストごとにコンテナを立てると、
 * 1 つの JVM で複数の Postgres が同時に立ち上がり、<strong>関係のないテストが
 * {@code initializationError} で落ちる</strong>——症状は毎回違うテストに出るため、
 * 原因が資源だと分かりにくい（IT7 で 4 回踏んだ形）。
 *
 * <p>他サービス（trackingms・handlingms・bookingms）と同じ形にしている。
 */
@SpringBootTest
@ActiveProfiles("integration")
abstract class BillingIntegrationTestBase {

    /** 業務タイムゾーン（{@code app.business-time-zone} の既定）。 */
    protected static final java.time.ZoneId BUSINESS_ZONE =
            java.time.ZoneId.of("Asia/Tokyo");

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
