package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.Adjustment;
import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.BillingShipperId;
import com.example.cargotracker.billing.domain.model.ChargeStatus;
import com.example.cargotracker.billing.domain.model.DiscountRate;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceAmounts;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceParties;
import com.example.cargotracker.billing.domain.model.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 精算書の算出と確定（US21 / US22）。
 *
 * <p><strong>算出と確定を分ける。</strong> 受入基準「算出結果を確認して確定操作が
 * できる」は、経理担当者が目で見て確かめる場を求めている。
 * 自動で確定すると確認の余地が無い。
 *
 * <p><strong>丸め後の値を保持し、再計算で導出しない。</strong> 税率や係数が将来
 * 変わっても、発行済み請求書の金額は変わってはならない。導出にすると、
 * <strong>税制改正の日に過去の請求書がすべて書き換わる</strong>。
 */
@DisplayName("精算書の算出と確定（US21 / US22）")
class InvoiceTest {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.1000");

    private static BillingBookingId booking() {
        return new BillingBookingId(UUID.randomUUID().toString());
    }

    private static Invoice 算出する(BigDecimal base, BigDecimal rate, boolean corporate) {
        return Invoice.calculate(
                new InvoiceParties(
                        InvoiceId.of("INV-20260501-0001"),
                        booking(),
                        new BillingShipperId(UUID.randomUUID().toString(), corporate)),
                Money.yen(base),
                rate == null ? null : DiscountRate.of(rate),
                TAX_RATE);
    }

    @Nested
    @DisplayName("算出")
    class 算出 {

        /** 設計書の計算例（基本料金 100,003 円・割引率 15%・税率 10%）。 */
        @Test
        void 設計書の計算例と一致する() {
            Invoice invoice = 算出する(new BigDecimal("100003"), new BigDecimal("0.15"), true);

            assertThat(invoice.baseAmount().value()).isEqualTo(new BigDecimal("100003"));
            assertThat(invoice.discountAmount().value())
                    .as("割引額 100,003 - 85,002 = 15,001")
                    .isEqualTo(new BigDecimal("15001"));
            assertThat(invoice.taxAmount().value()).isEqualTo(new BigDecimal("8500"));
            assertThat(invoice.totalAmount().value()).isEqualTo(new BigDecimal("93502"));
        }

        /**
         * <strong>個人荷主でも同じ形の精算書になる。</strong>
         *
         * <p>割引率 0% で計算を通す。<strong>割引の行が無い精算書を作らない。</strong>
         */
        @Test
        void 個人荷主は割引率ゼロで算出される() {
            Invoice invoice = 算出する(new BigDecimal("100000"), new BigDecimal("0.15"), false);

            assertThat(invoice.discountRate().isNone())
                    .as("契約率が渡っても個人には適用しない")
                    .isTrue();
            assertThat(invoice.discountAmount().isZero()).isTrue();
            assertThat(invoice.totalAmount().value()).isEqualTo(new BigDecimal("110000"));
        }

        /**
         * <strong>税率を精算書が持つ。</strong>
         *
         * <p>これが「税制改正の日に過去の請求書が書き換わらない」ことの鍵である。
         * 丸め後の金額だけを保存しても、税率を持たなければ根拠を再現できない。
         */
        @Test
        void 税率を精算書が持つ() {
            assertThat(算出する(new BigDecimal("1000"), null, false).taxRate())
                    .isEqualByComparingTo(TAX_RATE);
        }

        /** 算出した直後は下書きである。 */
        @Test
        void 算出した直後は下書きである() {
            Invoice invoice = 算出する(new BigDecimal("1000"), null, false);

            assertThat(invoice.chargeStatus()).isEqualTo(ChargeStatus.DRAFT);
            assertThat(invoice.isConfirmed()).isFalse();
        }
    }

    @Nested
    @DisplayName("料金調整")
    class 料金調整 {

        /** 例外（遅延・破損）が起きた貨物では、減額・補償費用を入力できる（受入基準 6）。 */
        @Test
        void 減額と補償費用を反映できる() {
            Invoice invoice = 算出する(new BigDecimal("100000"), null, false);

            invoice.adjust(new Adjustment(
                    Money.yen(new BigDecimal("10000")),
                    Money.yen(new BigDecimal("3000")),
                    "遅延による減額と代替輸送費"));

            assertThat(invoice.totalAmount().value())
                    .as("(100,000 - 10,000 + 3,000) × 1.10 = 102,300")
                    .isEqualTo(new BigDecimal("102300"));
        }

        /**
         * <strong>確定後は調整できない。</strong>
         *
         * <p>確定は経理担当者が金額を承認した印である。後から動かせるなら、
         * <strong>確定という操作に意味が無い</strong>。
         */
        @Test
        void 確定後は調整できない() {
            Invoice invoice = 算出する(new BigDecimal("100000"), null, false);
            invoice.confirmCharge();

            assertThatThrownBy(() -> invoice.adjust(
                    new Adjustment(Money.yen(BigDecimal.TEN), Money.zeroYen(), "後出しの減額")))
                    .isInstanceOf(IllegalStateException.class);
        }

        /** <strong>理由の無い調整は認めない。</strong> 後から見て根拠が説明できない。 */
        @Test
        void 理由の無い調整は認めない() {
            assertThatThrownBy(() -> new Adjustment(
                    Money.yen(BigDecimal.TEN), Money.zeroYen(), " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>請求額を超える減額は認めない。</strong>
         *
         * <p>返金は精算の取り消しを伴う別の業務である。
         * <strong>黙って負の請求書を作らない。</strong>
         */
        @Test
        void 請求額を超える減額は認めない() {
            Invoice invoice = 算出する(new BigDecimal("1000"), null, false);

            assertThatThrownBy(() -> invoice.adjust(new Adjustment(
                    Money.yen(new BigDecimal("1001")), Money.zeroYen(), "過大な減額")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("確定")
    class 確定 {

        @Test
        void 確定すると状態が変わる() {
            Invoice invoice = 算出する(new BigDecimal("1000"), null, false);

            invoice.confirmCharge();

            assertThat(invoice.chargeStatus()).isEqualTo(ChargeStatus.CONFIRMED);
            assertThat(invoice.isConfirmed()).isTrue();
        }

        /** <strong>二度目の確定は拒む。</strong> 確定は取り消せない操作である。 */
        @Test
        void 二度確定できない() {
            Invoice invoice = 算出する(new BigDecimal("1000"), null, false);
            invoice.confirmCharge();

            assertThatThrownBy(invoice::confirmCharge)
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * <strong>確定後は税率を変えても金額が動かない。</strong>
         *
         * <p>これが「丸め後の値を永続化し、再計算で導出しない」の意味である。
         * <strong>再計算する実装では、この検査が赤になる。</strong>
         */
        @Test
        void 確定後は税率が変わっても金額が動かない() {
            Invoice invoice = 算出する(new BigDecimal("100000"), null, false);
            invoice.confirmCharge();
            BigDecimal total = invoice.totalAmount().value();

            // 税率が 10% → 12% に変わった世界で読み直す（復元）
            Invoice restored = Invoice.reconstruct(
                    invoice.parties(), invoice.amounts(),
                    invoice.adjustment(), ChargeStatus.CONFIRMED, 0L);

            assertThat(restored.totalAmount().value())
                    .as("保存した金額をそのまま読む。再計算しない")
                    .isEqualTo(total);
            assertThat(restored.taxRate())
                    .as("そのときの税率も残る。根拠を再現できる")
                    .isEqualByComparingTo(TAX_RATE);
        }
    }

    @Nested
    @DisplayName("復元")
    class 復元 {

        /**
         * <strong>調整を持たない古い行も読める。</strong>
         *
         * <p>新しい不変条件で既存の行を読めなくしない
         * （V22 / V23 / V24 / V26 と同じ判断）。
         */
        @Test
        void 調整の無い行も読み戻せる() {
            Invoice restored = Invoice.reconstruct(
                    new InvoiceParties(
                            InvoiceId.of("INV-20260501-0002"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    new InvoiceAmounts(
                            Money.yen(new BigDecimal("1000")), DiscountRate.none(),
                            Money.zeroYen(), TAX_RATE, Money.yen(new BigDecimal("100")),
                            Money.yen(new BigDecimal("1100"))),
                    null, ChargeStatus.CONFIRMED, 0L);

            assertThat(restored.hasAdjustment()).isFalse();
            assertThat(restored.totalAmount().value()).isEqualTo(new BigDecimal("1100"));
        }
    }
}
