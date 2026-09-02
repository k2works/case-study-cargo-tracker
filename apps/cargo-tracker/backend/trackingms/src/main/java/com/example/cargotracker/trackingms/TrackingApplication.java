package com.example.cargotracker.trackingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Tracking サービスの起動クラス。 */
// shared の横断設定（起動時接続検査ほか）を取り込む。
@SpringBootApplication(scanBasePackages = {"com.example.cargotracker"})
public class TrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingApplication.class, args);
    }
}
