package com.example.billingms.domain.services;

import com.example.billingms.config.BillingProperties;
import com.example.billingms.domain.model.ShipperType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 支払期限算出ポリシー（US23、IT7 T4.2 / IT8 T1.9 で契約別支払サイト対応）。
 *
 * <p>請求書発行日（IssueInvoiceCommand 受理時の Clock 由来日付）に
 * {@link BillingProperties#paymentDueDays()} を加えて {@code payment_due} を確定する。
 * 月跨ぎ・閏年は {@link LocalDate#plusDays(long)} の標準実装に委ねる。</p>
 *
 * <p>IT8 T1.9: 荷主契約ごとの支払サイト（NET30 / NET60 / NET90）対応。
 * {@link BillingProperties#paymentDueDaysByType()} の Map から
 * {@link ShipperType} 別の日数を取得し、未設定なら {@link BillingProperties#paymentDueDays()}
 * の default にフォールバック。Invoice 集約での {@code shipperType} 保持と
 * IssueInvoiceCommand での経路統合は IT9 持ち越し。</p>
 */
@Component
public class PaymentDuePolicy {

    private final BillingProperties properties;

    public PaymentDuePolicy(BillingProperties properties) {
        this.properties = properties;
    }

    /**
     * 発行日から支払期限日を算出する（default 日数を使用、既存互換）。
     *
     * @param issuedDate 請求書発行日
     * @return 支払期限日（発行日 + {@link BillingProperties#paymentDueDays()} 日）
     */
    public LocalDate calculateDueDate(LocalDate issuedDate) {
        return calculateDueDate(issuedDate, null);
    }

    /**
     * 発行日と荷主種別から支払期限日を算出する（IT8 T1.9 / NET30/60/90 対応）。
     *
     * @param issuedDate  請求書発行日
     * @param shipperType 荷主種別（null 時は default）
     * @return 支払期限日
     */
    public LocalDate calculateDueDate(LocalDate issuedDate, ShipperType shipperType) {
        Objects.requireNonNull(issuedDate, "issuedDate は必須です");
        return issuedDate.plusDays(properties.paymentDueDaysFor(shipperType));
    }
}
