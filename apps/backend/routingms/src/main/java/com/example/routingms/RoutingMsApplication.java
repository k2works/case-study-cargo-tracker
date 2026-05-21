package com.example.routingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * routingms（経路設計マイクロサービス）のエントリポイント。
 *
 * <p>航海スケジュール管理（VoyageSchedule 集約）と経路候補算出を担う。
 * Axon Framework 5 による CQRS + Event Sourcing を採用し、イベントは
 * Axon Kafka Extension 経由で Kafka に配信される。</p>
 */
@SpringBootApplication
public class RoutingMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingMsApplication.class, args);
    }
}
