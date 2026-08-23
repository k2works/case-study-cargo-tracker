package com.example.trackingms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 実 DB を使う検査の土台。
 *
 * <p><strong>DB は 1 つを共有し、止めない。</strong>{@code @Container} の静的フィールドは
 * それを宣言したクラスの終了時に止まる。土台を継承するテストが 2 つあると、先に終わった
 * ほうが DB を止め、後のほうは接続できない。起動しっぱなしにして JVM の終了に任せる
 * （Testcontainers の Ryuk が後片付けする）。
 *
 * <p><strong>テストごとにコンテナを立てない。</strong>1 つの JVM で 3 つの Postgres と
 * RabbitMQ が同時に立ち上がると、資源が足りずに<strong>関係のないテストが
 * {@code initializationError} で落ちる</strong>——症状は毎回違うテストに出るため、
 * 原因が資源だと分かりにくい（IT7 で 4 回踏んだ形）。
 *
 * <p>Spring の文脈は同じ構成なら使い回されるため、継承しても文脈が増えることはない。
 */
@SpringBootTest
@ActiveProfiles("integration")
abstract class TrackingIntegrationTestBase {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
