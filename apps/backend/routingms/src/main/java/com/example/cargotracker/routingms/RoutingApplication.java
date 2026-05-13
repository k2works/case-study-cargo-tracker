package com.example.cargotracker.routingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 経路設計マイクロサービス（routingms）のエントリポイント。
 *
 * <p>役割: 航海スケジュール管理（US24 / US25）、経路候補算出（US07 / US08）、
 * 経路選択・確定（US09-US13）の Command/Event/Query Side を提供する。</p>
 */
@SpringBootApplication
public class RoutingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }
}
