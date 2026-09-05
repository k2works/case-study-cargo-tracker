package com.example.cargotracker.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.example.cargotracker.shared.infrastructure.axon.AxonJdbcConfiguration;
import com.example.cargotracker.shared.infrastructure.axon.AxonServerStartupCheckConfiguration;
import com.example.cargotracker.shared.infrastructure.crypto.CryptoConfiguration;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;

/** Billing サービスの起動クラス。 */
// 共有設定は必要なものだけを明示的に取り込む。一括スキャンにすると、
// DataSource を持たない gatewayms が JDBC の設定を読み込んで起動に失敗する。
@SpringBootApplication
@Import({
    AxonJdbcConfiguration.class,
    AxonServerStartupCheckConfiguration.class,
    BusinessClockConfiguration.class,
    // ADR-0003。ShipperRegisteredEvent の氏名は暗号化されて届く。同じ変換を
    // 入れないと、契約スナップショットにエンベロープの JSON がそのまま入る。
    CryptoConfiguration.class,
})
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
