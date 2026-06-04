package com.example.billingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * billingms 全体で共有する基盤 Bean を定義する設定クラス。
 *
 * <p>現状は {@link Clock} のみを公開する。{@code Clock} は以下の用途で注入される
 * （テスト時には固定 {@code Clock} に差し替え可能）:</p>
 *
 * <ul>
 *   <li>{@code CorporateDiscountPolicy} / {@code FareCalculator} 等のドメインサービスでの時刻取得</li>
 *   <li>{@code InvoiceNumberGenerator} で {@code INV-YYYYMMDD-XXXX} 形式の請求書番号採番</li>
 *   <li>{@code PaymentDuePolicy} で支払期限（発行日 + 30 日）の確定</li>
 *   <li>{@code OverdueScheduler} で支払期限超過の判定（{@code payment_due < now()}）</li>
 * </ul>
 */
@Configuration
public class BillingCommonConfig {

    /**
     * システム時刻を返す既定の {@link Clock}。テストでは固定 Clock に差し替えて時刻依存テストを安定化させる。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
