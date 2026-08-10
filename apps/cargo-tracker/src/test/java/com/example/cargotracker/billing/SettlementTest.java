package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.BillingShipperId;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceParties;
import com.example.cargotracker.billing.domain.model.Issuance;
import com.example.cargotracker.billing.domain.model.Money;
import com.example.cargotracker.billing.domain.model.Payment;
import com.example.cargotracker.billing.domain.model.PaymentMethod;
import com.example.cargotracker.billing.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 精算の処理（US23）。
 *
 * <p><strong>2 つの軸を混ぜない</strong>（ADR-017）。料金の状態と支払いの状態は
 * 別に動く。「料金は確定したが未入金」と「料金が未確定」を区別できないと、
 * <strong>督促の対象を選べない</strong>。
 *
 * <p><strong>期限は日付で判断する。</strong> 時刻付きで比べると、期限当日の入金が
 * 「1 秒過ぎている」で督促対象になる（DATE と TIMESTAMP を素朴に比べた過去の型）。
 */
@DisplayName("精算の処理（US23）")
class SettlementTest {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.1000");
    private static final Instant ISSUED_AT = Instant.parse("2026-05-01T00:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 5, 31);

    /** 確定済みの請求書（請求総額 1,100 円）。 */
    private static Invoice 確定済みの請求書() {
        Invoice invoice = Invoice.calculate(
                InvoiceParties.of(
                        InvoiceId.of("INV-00009001"),
                        new BillingBookingId(UUID.randomUUID().toString()),
                        new BillingShipperId(UUID.randomUUID().toString(), false)),
                Money.yen(new BigDecimal("1000")), null, TAX_RATE);
        invoice.confirmCharge();
        return invoice;
    }

    private static Payment 入金(Money amount) {
        return new Payment(amount, Instant.parse("2026-05-20T00:00:00Z"),
                PaymentMethod.BANK_TRANSFER, "TX-0001");
    }

    @Nested
    @DisplayName("精算書の発行")
    class 発行 {

        /** 受入基準 1: 「確定」状態の輸送料金をもとに精算書を発行できる。 */
        @Test
        void 確定済みなら発行できる() {
            Invoice invoice = 確定済みの請求書();

            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            assertThat(invoice.isIssued()).isTrue();
            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(invoice.issuance().dueDate()).isEqualTo(DUE_DATE);
        }

        /**
         * <strong>下書きからは発行できない。</strong>
         *
         * <p>確定は経理担当者が金額を承認した印である。承認前の金額で請求書を出すと、
         * <strong>荷主に伝えた後で金額が変わる</strong>。
         */
        @Test
        void 下書きからは発行できない() {
            Invoice draft = Invoice.calculate(
                    InvoiceParties.of(
                            InvoiceId.of("INV-00009002"),
                            new BillingBookingId(UUID.randomUUID().toString()),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    Money.yen(new BigDecimal("1000")), null, TAX_RATE);

            assertThatThrownBy(() -> draft.issue(new Issuance(ISSUED_AT, DUE_DATE)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("確定");
        }

        /** <strong>二重発行を拒む。</strong> 同じ請求書を 2 通送ることになる。 */
        @Test
        void 二度は発行できない() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            assertThatThrownBy(() -> invoice.issue(new Issuance(ISSUED_AT, DUE_DATE)))
                    .isInstanceOf(IllegalStateException.class);
        }

        /** <strong>発行していない請求書は金額も動かせない。</strong> */
        @Test
        void 発行前は未入金ですらない() {
            assertThat(確定済みの請求書().isIssued()).isFalse();
        }
    }

    @Nested
    @DisplayName("入金の確認")
    class 入金確認 {

        /** 受入基準 4: 入金確認後、精算状態が「精算済」に更新される。 */
        @Test
        void 請求額どおりの入金を確認できる() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100"))));

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
            assertThat(invoice.payment().method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        }

        /**
         * <strong>一部入金は認めない</strong>（設計判断）。
         *
         * <p>差額の扱いは業務である（不足は再請求）。<strong>システムが黙って受けると
         * 帳簿と合わなくなる</strong>。
         */
        @Test
        void 請求額に満たない入金は拒む() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            assertThatThrownBy(() ->
                    invoice.confirmPayment(入金(Money.yen(new BigDecimal("1000")))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("請求額");
        }

        /** <strong>過入金も拒む。</strong> 返金は別の業務である。 */
        @Test
        void 請求額を超える入金も拒む() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            assertThatThrownBy(() ->
                    invoice.confirmPayment(入金(Money.yen(new BigDecimal("2000")))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** <strong>発行していない請求書に入金は無い。</strong> */
        @Test
        void 発行前は入金を確認できない() {
            Invoice invoice = 確定済みの請求書();

            assertThatThrownBy(() ->
                    invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100")))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("発行");
        }

        /** <strong>二重入金を拒む。</strong> 同じ入金を 2 回記録すると帳簿が狂う。 */
        @Test
        void 二度は入金を確認できない() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));
            invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100"))));

            assertThatThrownBy(() ->
                    invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100")))))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * <strong>遅れても入金は入金である。</strong>
         *
         * <p>期限超過の請求書に入金確認ができないと、入金があったのに記録できず、
         * 督促の一覧に残り続ける。
         */
        @Test
        void 期限を過ぎていても入金を確認できる() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));
            invoice.markOverdue(LocalDate.of(2026, 6, 10));

            invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100"))));

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("支払期限")
    class 期限 {

        /** <strong>期限当日は超過ではない。</strong> 約束を守っている相手に催促しない。 */
        @Test
        void 期限当日は超過ではない() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            invoice.markOverdue(DUE_DATE);

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        /** 受入基準 5: 支払期限超過を経理担当者が気づける。 */
        @Test
        void 期限の翌日から超過になる() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));

            invoice.markOverdue(DUE_DATE.plusDays(1));

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
            assertThat(invoice.issuance().daysOverdue(DUE_DATE.plusDays(3))).isEqualTo(3L);
        }

        /**
         * <strong>入金済みを期限超過に戻さない。</strong>
         *
         * <p>画面を開くたびに判定するため、<strong>入金確認の後に開いた画面で
         * 督促対象に戻る</strong>形を作らない。
         */
        @Test
        void 入金済みは期限を過ぎても超過にならない() {
            Invoice invoice = 確定済みの請求書();
            invoice.issue(new Issuance(ISSUED_AT, DUE_DATE));
            invoice.confirmPayment(入金(Money.yen(new BigDecimal("1100"))));

            invoice.markOverdue(DUE_DATE.plusDays(30));

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        }

        /** <strong>発行していない請求書は期限を持たない。</strong> */
        @Test
        void 発行前は期限超過にならない() {
            Invoice invoice = 確定済みの請求書();

            invoice.markOverdue(DUE_DATE.plusDays(30));

            assertThat(invoice.isIssued()).isFalse();
        }
    }
}
