package com.example.cargotracker.trackingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 追跡マイクロサービス（trackingms）のエントリポイント。
 *
 * <p>役割: 貨物の現在状態・追跡履歴を管理し、公開 URL から JWT 時限トークンで
 * 照会できる Query Side を提供する（US18）。IT6 以降に US17・US19・US20 を順次取り込む。</p>
 *
 * <p>関連 ADR: ADR-0012 / ADR-0013。</p>
 */
@SpringBootApplication
public class TrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingApplication.class, args);
    }
}
