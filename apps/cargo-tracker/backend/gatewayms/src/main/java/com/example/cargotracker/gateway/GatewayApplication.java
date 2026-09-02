package com.example.cargotracker.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Gateway サービスの起動クラス。 */
// shared の横断設定（起動時接続検査ほか）を取り込む。
@SpringBootApplication(scanBasePackages = {"com.example.cargotracker"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
