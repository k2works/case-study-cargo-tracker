package com.example.trackingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * trackingms（追跡マイクロサービス）のエントリポイント。
 *
 * <p>貨物の追跡情報（TrackingActivity 集約）と輸送状態の管理、追跡例外の記録を担う。
 * Axon Framework 5 による CQRS + Event Sourcing を採用し、bookingms からの
 * {@code TrackingIssuanceRequestedEvent}（IT5 タスク 1.2）および handlingms からの
 * {@code HandlingActivityRegisteredEvent}（IT5 タスク 3.3）を Axon Kafka Extension
 * 経由で購読する。受信ハンドラは ADR-0011（ホワイトリスト方式）に従う。</p>
 */
@SpringBootApplication
public class TrackingMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingMsApplication.class, args);
    }
}
