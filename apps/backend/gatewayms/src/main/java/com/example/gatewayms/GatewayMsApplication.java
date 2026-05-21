package com.example.gatewayms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gatewayms（API Gateway）のエントリポイント。
 *
 * <p>Spring Cloud Gateway によるバックエンド入口。JWT を検証して各
 * マイクロサービスにルーティングする。リアクティブスタック（WebFlux）で
 * 動作する。</p>
 */
@SpringBootApplication
public class GatewayMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayMsApplication.class, args);
    }
}
