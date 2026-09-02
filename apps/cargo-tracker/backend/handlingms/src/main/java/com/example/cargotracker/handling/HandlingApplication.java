package com.example.cargotracker.handling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Handling サービスの起動クラス。 */
// shared の横断設定（起動時接続検査ほか）を取り込む。
@SpringBootApplication(scanBasePackages = {"com.example.cargotracker"})
public class HandlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandlingApplication.class, args);
    }
}
