package com.example.cargotracker.handlingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 荷役マイクロサービス（handlingms）のエントリポイント。
 *
 * <p>役割: 港湾での荷役作業記録（US15 / US16）と貨物状態手動更新（US17 暫定）の
 * Command/Event/Query Side を提供する。</p>
 *
 * <p>関連 ADR: ADR-0012 handlingms と trackingms の責務分離。</p>
 */
@SpringBootApplication
public class HandlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandlingApplication.class, args);
    }
}
