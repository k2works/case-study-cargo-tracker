package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.valueobjects.Adjustment;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingBookingId;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingShipperId;
import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceAmounts;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceParties;
import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceType;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
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
                InvoiceParties.of(
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
    @DisplayName("画面に出す内訳の整合")
    class 内訳の整合 {

        /**
         * <strong>割引後料金 + 消費税 = 請求総額</strong>（レビュー H1）。
         *
         * <p>経理担当者はこの表を電卓で検算する。<strong>足し算が合わない表は、
         * それだけで請求全体が信用されない。</strong>
         *
         * <p><strong>調整と割引が同時にある場合に壊れていた。</strong> 実装の計算順序は
         * 基本料金 → 調整 → 割引 → 消費税であり、割引は<strong>調整後の額</strong>に掛かる。
         * 画面が「基本料金 − 割引額」で割引後料金を作ると、調整の分だけずれる。
         * <strong>調整なしのテストしか無かったため、3 視点のレビューまで気づかなかった。</strong>
         */
        @Test
        void 割引後料金と消費税の和が請求総額と一致する() {
            Invoice invoice = 算出する(new BigDecimal("100000"), new BigDecimal("0.15"), true);
            invoice.adjust(new Adjustment(
                    Money.yen(new BigDecimal("10000")),
                    Money.yen(new BigDecimal("3000")),
                    "遅延による減額と代替輸送費"));

            // (100,000 - 10,000 + 3,000) = 93,000 → × 0.85 = 79,050
            BigDecimal discounted =
                    invoice.totalAmount().value().subtract(invoice.taxAmount().value());

            assertThat(discounted)
                    .as("割引後料金は調整後の額に割引を掛けた値である")
                    .isEqualTo(new BigDecimal("79050"));
            assertThat(discounted.add(invoice.taxAmount().value()))
                    .as("表の足し算が合う")
                    .isEqualTo(invoice.totalAmount().value());
        }

        /**
         * <strong>割引額は調整後の額に対する差である。</strong>
         *
         * <p>「基本料金 − 割引額」を割引後料金と読むと、調整がある請求書でずれる。
         */
        @Test
        void 割引額は調整後の額に対する差である() {
            Invoice invoice = 算出する(new BigDecimal("100000"), new BigDecimal("0.15"), true);
            invoice.adjust(new Adjustment(
                    Money.yen(new BigDecimal("10000")),
                    Money.yen(new BigDecimal("3000")),
                    "遅延による減額と代替輸送費"));

            assertThat(invoice.discountAmount().value())
                    .as("93,000 - 79,050 = 13,950。基本料金 100,000 に対する差ではない")
                    .isEqualTo(new BigDecimal("13950"));
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
         *
         * <p><strong>元の版は常に緑になる空振りだった</strong>（レビュー H5）。
         * {@code reconstruct} に渡していたのが保存値そのものだったため、
         * <strong>再計算する実装でも同じ値が出た</strong>。
         * <strong>税率だけを差し替えて</strong>、金額が動かないことを見る。
         */
        @Test
        void 確定後は税率が変わっても金額が動かない() {
            Invoice invoice = 算出する(new BigDecimal("100000"), null, false);
            invoice.confirmCharge();
            BigDecimal total = invoice.totalAmount().value();
            BigDecimal tax = invoice.taxAmount().value();

            // **税率が 10% → 12% に変わった世界**を作る（金額は保存値のまま）
            InvoiceAmounts changedRate = new InvoiceAmounts(
                    invoice.baseAmount(), invoice.discountRate(), invoice.discountAmount(),
                    new BigDecimal("0.1200"), invoice.taxAmount(), invoice.totalAmount());
            Invoice restored = Invoice.reconstruct(
                    invoice.parties(), changedRate,
                    invoice.adjustment(), ChargeStatus.CONFIRMED, 0L);

            assertThat(restored.totalAmount().value())
                    .as("保存した金額をそのまま読む。税率で再計算しない")
                    .isEqualTo(total);
            assertThat(restored.taxAmount().value())
                    .as("消費税額も動かない（12% で計算し直さない）")
                    .isEqualTo(tax);
        }

        /**
         * <strong>税率は請求書ごとに保持される。</strong>
         *
         * <p>金額だけを保存しても根拠を再現できない。
         * <strong>同じ基本料金でも税率が違えば別の請求書になる。</strong>
         */
        @Test
        void 異なる税率で算出した請求書はそれぞれの税率を持つ() {
            Invoice tenPercent = Invoice.calculate(
                    InvoiceParties.of(InvoiceId.of("INV-20260501-0003"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    Money.yen(new BigDecimal("100000")), null, new BigDecimal("0.1000"));
            Invoice eightPercent = Invoice.calculate(
                    InvoiceParties.of(InvoiceId.of("INV-20260501-0004"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    Money.yen(new BigDecimal("100000")), null, new BigDecimal("0.0800"));

            assertThat(tenPercent.totalAmount().value()).isEqualTo(new BigDecimal("110000"));
            assertThat(eightPercent.totalAmount().value()).isEqualTo(new BigDecimal("108000"));
            assertThat(eightPercent.taxRate()).isEqualByComparingTo(new BigDecimal("0.0800"));
        }
    }

    @Nested
    @DisplayName("種別（US30）")
    class 種別 {

        /**
         * <strong>キャンセル料の請求書は輸送料金の請求書と並ぶ。</strong>
         *
         * <p>予約ごとに 1 枚しか作れない構造では、輸送中にキャンセルした荷主へ
         * キャンセル料を請求する手段が無い（X1）。
         */
        @Test
        void 輸送実績から算出した請求書は輸送料金である() {
            assertThat(算出する(new BigDecimal("100000"), null, false).invoiceType())
                    .isEqualTo(InvoiceType.TRANSPORT);
        }

        /**
         * <strong>キャンセル料に割引は適用しない。</strong>
         *
         * <p>割引は「運びきったこと」への取引条件であり、運びきらなかった輸送に
         * 適用する理由が無い。<strong>割引率 30% の法人でも料率どおりに請求する。</strong>
         */
        @Test
        void キャンセル料は割引を適用しない() {
            Invoice fee = Invoice.cancellationFee(
                    InvoiceParties.of(
                            InvoiceId.of("INV-20260810-0001"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), true)),
                    Money.yen(new BigDecimal("100000")),
                    new BigDecimal("0.50"), TAX_RATE);

            assertThat(fee.invoiceType()).isEqualTo(InvoiceType.CANCELLATION);
            assertThat(fee.baseAmount().value())
                    .as("基準は輸送料金の基本料金であり、その 50% を請求する")
                    .isEqualTo(new BigDecimal("50000"));
            assertThat(fee.discountAmount().value())
                    .as("法人でも割引しない")
                    .isEqualTo(BigDecimal.ZERO);
            assertThat(fee.totalAmount().value()).isEqualTo(new BigDecimal("55000"));
        }

        /**
         * <strong>キャンセル料も経理担当者が確認してから確定する。</strong>
         *
         * <p>承認と同時に確定すると、金額を目で見る場が無くなる（US21 と同じ判断）。
         */
        @Test
        void キャンセル料の請求書も下書きで始まる() {
            Invoice fee = Invoice.cancellationFee(
                    InvoiceParties.of(
                            InvoiceId.of("INV-20260810-0002"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    Money.yen(new BigDecimal("100000")),
                    new BigDecimal("0.20"), TAX_RATE);

            assertThat(fee.isConfirmed()).isFalse();
            assertThat(fee.baseAmount().value()).isEqualTo(new BigDecimal("20000"));
        }

        /**
         * <strong>料率 0 でキャンセル料の請求書を作らない。</strong>
         *
         * <p>0 円の請求書は送る相手がいない。受入基準も
         * 「キャンセル料が<strong>発生する場合</strong>」と書いている。
         */
        @Test
        void 料率がゼロならキャンセル料の請求書は作れない() {
            assertThatThrownBy(() -> Invoice.cancellationFee(
                    InvoiceParties.of(
                            InvoiceId.of("INV-20260810-0003"), booking(),
                            new BillingShipperId(UUID.randomUUID().toString(), false)),
                    Money.yen(new BigDecimal("100000")),
                    BigDecimal.ZERO, TAX_RATE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("料率");
        }
    }

    @Nested
    @DisplayName("復元")
    class 復元 {

        /**
         * <strong>種別を持たない行は輸送料金として読める。</strong>
         *
         * <p>列が無かったころの行を拒むと、その請求書の画面ごと開けなくなる
         * （V22 / V26 / V32 と同じ判断）。
         */
        @Test
        void 種別の無い行は輸送料金として読み戻せる() {
            assertThat(InvoiceType.ofRestored(null)).isEqualTo(InvoiceType.TRANSPORT);
            assertThat(InvoiceType.ofRestored("")).isEqualTo(InvoiceType.TRANSPORT);
            assertThat(InvoiceType.ofRestored("読めない値"))
                    .isEqualTo(InvoiceType.TRANSPORT);
            assertThat(InvoiceType.ofRestored("CANCELLATION"))
                    .isEqualTo(InvoiceType.CANCELLATION);
        }


        /**
         * <strong>調整を持たない古い行も読める。</strong>
         *
         * <p>新しい不変条件で既存の行を読めなくしない
         * （V22 / V23 / V24 / V26 と同じ判断）。
         */
        @Test
        void 調整の無い行も読み戻せる() {
            Invoice restored = Invoice.reconstruct(
                    InvoiceParties.of(
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
