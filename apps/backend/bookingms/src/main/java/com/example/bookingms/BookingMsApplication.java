package com.example.bookingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bookingms（予約マイクロサービス）のエントリポイント。
 *
 * <p>貨物予約（Cargo 集約）・荷主（Shipper 集約）・見積（Quotation 集約）の中核ロジックを担う。
 * Axon Framework 5 + Axon Kafka Extension による CQRS + Event Sourcing 構成で、
 * BookingSagaManager が経路割り当て〜追跡番号発行までを調整する。</p>
 */
@SpringBootApplication
public class BookingMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingMsApplication.class, args);
    }
}
