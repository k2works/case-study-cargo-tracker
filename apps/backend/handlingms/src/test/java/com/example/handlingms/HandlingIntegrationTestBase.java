package com.example.handlingms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 実 DB を使う検査の土台（[IT8 ふりかえり](../../../../../../../docs/development/retrospective-8.md) Try 6）。
 *
 * <p><strong>2 つ目の統合テストを書く前に置く。</strong>trackingms では 3 つ目を足した
 * ところで Postgres が 3 つ立ち上がり、RabbitMQ の起動が資源不足で落ちた。症状は毎回
 * 違うテストに {@code initializationError} として出るため、原因が資源だと分かりにくい
 * （IT7 で 4 回、IT8 で 1 回踏んだ）。<strong>踏んでから直すのではなく、書く前に置く。</strong>
 *
 * <p><strong>DB は 1 つを共有し、止めない。</strong>{@code @Container} の静的フィールドは
 * それを宣言したクラスの終了時に止まる。土台を継承するテストが 2 つあると、先に終わった
 * ほうが DB を止め、後のほうは接続できない。起動しっぱなしにして JVM の終了に任せる
 * （Testcontainers の Ryuk が後片付けする）。
 *
 * <p>Spring の文脈は同じ構成なら使い回されるため、継承しても文脈が増えることはない。
 */
@SpringBootTest
@ActiveProfiles("integration")
abstract class HandlingIntegrationTestBase {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
