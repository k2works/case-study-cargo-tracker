package com.example.billingms.config;

import com.example.billingms.domain.model.RateTable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * billingms 全体で共有する基盤 Bean を定義する設定クラス。
 *
 * <ul>
 *   <li>{@link Clock}: ドメインサービス・Scheduler 等の時刻取得（テスト時固定 Clock 差し替え可）
 *     <ul>
 *       <li>{@code CorporateDiscountPolicy} / {@code FareCalculator} 等のドメインサービスでの時刻取得</li>
 *       <li>{@code InvoiceNumberGenerator} で {@code INV-YYYYMMDD-XXXX} 形式の請求書番号採番</li>
 *       <li>{@code PaymentDuePolicy} で支払期限（発行日 + 30 日）の確定</li>
 *       <li>{@code OverdueScheduler} で支払期限超過の判定（{@code payment_due < now()}）</li>
 *     </ul>
 *   </li>
 *   <li>{@link RateTable}: 料金単価表（IT7 はコード内定数、IT8 で運用設定 DB へ移行検討）
 *     <ul>
 *       <li>{@code FareCalculator} が基本料金計算で参照</li>
 *     </ul>
 *   </li>
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

    /**
     * default 料金単価表（S20 UI サンプル値と整合）。IT7 では Bean として固定単価を公開、
     * IT8 以降で経理担当者による料金改定（DB 永続化）への移行を検討する。
     */
    @Bean
    public RateTable rateTable() {
        return RateTable.defaultTable();
    }
}
