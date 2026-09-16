package com.example.cargotracker.handling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.example.cargotracker.shared.infrastructure.axon.AxonJdbcConfiguration;
import com.example.cargotracker.shared.infrastructure.axon.AxonServerStartupCheckConfiguration;
import com.example.cargotracker.shared.infrastructure.axon.DeadLetterController;
import com.example.cargotracker.shared.infrastructure.axon.DeadLetterRetryEndpoint;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcherConfiguration;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;

/** Handling サービスの起動クラス。 */
// 共有設定は必要なものだけを明示的に取り込む。一括スキャンにすると、
// DataSource を持たない gatewayms が JDBC の設定を読み込んで起動に失敗する。
@SpringBootApplication
@Import({
    // 退避したイベントを処理し直す入口（ADR-0014 決定 1）。**消す手段は置かない。**
    DeadLetterRetryEndpoint.class,
    // 退避を画面から読む口（IT15 引き継ぎ 1）。端末からしか触れないと、
    // 投影が止まっていることに気づけるのは端末を持つ人だけになる。
    DeadLetterController.class,
    AxonJdbcConfiguration.class,
    AxonServerStartupCheckConfiguration.class,
    BusinessClockConfiguration.class,
    QueryDispatcherConfiguration.class,
})
public class HandlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandlingApplication.class, args);
    }
}
