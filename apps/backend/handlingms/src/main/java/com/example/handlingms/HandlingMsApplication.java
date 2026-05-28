package com.example.handlingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * handlingms（荷役マイクロサービス）のエントリポイント。
 *
 * <p>港湾での荷役作業（HandlingActivity 集約：受領 RECEIVE・積込 LOAD・荷降し UNLOAD・
 * 引取 CLAIM・税関通過 CUSTOMS）の記録を担う。Axon Framework 5 による CQRS + Event Sourcing を
 * 採用し、bookingms の {@code CargoBookedEvent} / {@code CargoRoutedEvent} を CargoSnapshot ACL
 * として購読（IT5 タスク 3.1）、{@code HandlingActivityRegisteredEvent}（shared）を発行して
 * trackingms に状態更新を依頼する（IT5 タスク 3.3）。受信ハンドラは ADR-0011（ホワイトリスト方式）に従う。</p>
 */
@SpringBootApplication
public class HandlingMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandlingMsApplication.class, args);
    }
}
