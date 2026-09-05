package com.example.cargotracker.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.example.cargotracker.shared.infrastructure.axon.AxonJdbcConfiguration;
import com.example.cargotracker.shared.infrastructure.axon.AxonServerStartupCheckConfiguration;
import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;

/** Auth サービスの起動クラス。 */
// 共有設定は必要なものだけを明示的に取り込む。一括スキャンにすると、
// DataSource を持たない gatewayms が JDBC の設定を読み込んで起動に失敗する。
@SpringBootApplication
@Import({
    AxonJdbcConfiguration.class,
    AxonServerStartupCheckConfiguration.class,
    BusinessClockConfiguration.class,
})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
