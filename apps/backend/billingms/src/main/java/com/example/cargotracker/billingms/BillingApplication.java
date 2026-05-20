package com.example.cargotracker.billingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 精算マイクロサービス（billingms）のエントリポイント。
 *
 * <p>役割: Invoice 集約を中心に輸送料金算出・法人割引適用・精算処理を管理する（US21-US23）。</p>
 */
@SpringBootApplication
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
