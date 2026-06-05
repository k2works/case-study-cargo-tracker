package com.example.billingms.domain.services;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 支払期限算出ポリシー（US23、IT7 T4.2、iteration_plan-7.md L111）。
 *
 * <p>請求書発行日（IssueInvoiceCommand 受理時の Clock 由来日付）に固定オフセット（30 日）を
 * 加えて {@code payment_due} を確定する。月跨ぎ・閏年は {@link LocalDate#plusDays(long)} の
 * 標準実装に委ねる。</p>
 *
 * <p>IT8 で荷主契約ごとの支払サイト（NET30 / NET60 / NET90）対応を予定。</p>
 */
@Component
public class PaymentDuePolicy {

    /** デフォルト支払サイト（日数）。 */
    private static final int DEFAULT_DAYS = 30;

    /**
     * 発行日から支払期限日を算出する。
     *
     * @param issuedDate 請求書発行日
     * @return 支払期限日（発行日 + 30 日）
     */
    public LocalDate calculateDueDate(LocalDate issuedDate) {
        Objects.requireNonNull(issuedDate, "issuedDate は必須です");
        return issuedDate.plusDays(DEFAULT_DAYS);
    }
}
