package com.example.billingms.domain.services;

import com.example.billingms.config.BillingProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 支払期限算出ポリシー（US23、IT7 T4.2、iteration_plan-7.md L111）。
 *
 * <p>請求書発行日（IssueInvoiceCommand 受理時の Clock 由来日付）に
 * {@link BillingProperties#paymentDueDays()} を加えて {@code payment_due} を確定する。
 * 月跨ぎ・閏年は {@link LocalDate#plusDays(long)} の標準実装に委ねる。</p>
 *
 * <p>IT7 review 中対応で 30 日固定からプロパティ駆動に変更。IT8 で荷主契約ごとの
 * 支払サイト（NET30 / NET60 / NET90）対応時は {@link BillingProperties} を
 * {@code Map<ShipperType, Integer>} 等に拡張する。</p>
 */
@Component
public class PaymentDuePolicy {

    private final int defaultDays;

    public PaymentDuePolicy(BillingProperties properties) {
        this.defaultDays = properties.paymentDueDays();
    }

    /**
     * 発行日から支払期限日を算出する。
     *
     * @param issuedDate 請求書発行日
     * @return 支払期限日（発行日 + 設定日数）
     */
    public LocalDate calculateDueDate(LocalDate issuedDate) {
        Objects.requireNonNull(issuedDate, "issuedDate は必須です");
        return issuedDate.plusDays(defaultDays);
    }
}
