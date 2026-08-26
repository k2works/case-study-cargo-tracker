package com.example.billingms.domain.model;

import static com.example.billingms.ChargeFixtures.domesticLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 精算書（US21・[ADR-027] 決定 3・決定 4）。
 *
 * <p><strong>算出中の精算書は存在しない</strong>（決定 3）。経理担当者が確定操作をした
 * 時点で初めて発行される。`PaymentStatus` に `DRAFT` を足さないのは、正典の 4 値が
 * <strong>支払いの状態</strong>を表しており、そこに「金額を確定したか」を混ぜると
 * `CONFIRMED` が 2 つの意味を持つためである。
 *
 * <p><strong>発行した精算書の金額は動かない</strong>（決定 4）。請求書は荷主へ出す約束で
 * あり、出したあとに黙って変わると請求の根拠が消える。
 */
@DisplayName("精算書")
class InvoiceTest {

    /** 業務タイムゾーン（`app.business-time-zone` の既定）。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    private static final InvoiceId ID = InvoiceId.of("INV-2026000001");
    private static final BillingBookingId BOOKING = BillingBookingId.of("BKG-2026000007");
    private static final BillingShipperId SHIPPER = BillingShipperId.corporate("1", "丸紅商事株式会社");
    private static final Instant ISSUED_AT = Instant.parse("2027-10-01T00:00:00Z");

    /** 50,000 × 2 区間 × 4.2 × 1.0 = 420,000 円。 */
    private static final TransportCharge CHARGE =
            TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL);

    /**
     * 検査の中で組み立てないための定数。
     *
     * <p>ラムダの中で {@code DiscountPolicy.none()} を呼ぶと、<strong>どの呼び出しが
     * 例外を投げたのか</strong>が判別できない——確かめたいのは {@code Invoice.issue} が
     * 断ることであって、割引方針の生成ではない。
     */
    private static final DiscountPolicy NO_DISCOUNT = DiscountPolicy.none();
    private static final TaxRate TAX = TaxRate.standard();
    private static final List<InvoiceLineItem> NO_ADJUSTMENTS = List.of();

    /** 金額の材料。**検査の中で組み立てない**——確かめたいのは Invoice の振る舞いである。 */
    private static final InvoiceCharges CHARGES =
            InvoiceCharges.of(CHARGE, DiscountPolicy.none(), TaxRate.standard());

    private static Invoice issue(DiscountPolicy policy, List<InvoiceLineItem> adjustments,
            CancellationFee fee) {
        return Invoice.issue(new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT), new InvoiceCharges(CHARGE, policy, fee, TAX), adjustments, BUSINESS_ZONE);
    }

    @Nested
    @DisplayName("発行")
    class Issuing {

        /** <strong>発行の時点では未入金である</strong>（決定 3）。入金の確認は US23。 */
        @Test
        @DisplayName("発行した精算書は未入金である")
        void isPendingWhenIssued() {
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), null);

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(invoice.issuedAt()).isEqualTo(ISSUED_AT);
        }

        /**
         * <strong>合計は「基本料金 − 割引 + キャンセル料 + 調整 + 消費税」である。</strong>
         *
         * <p>420,000 − 42,000 = 378,000。消費税 37,800。合計 415,800。
         */
        @Test
        @DisplayName("割引と消費税を含めて合計を出す")
        void sumsUpTheTotal() {
            Invoice invoice = issue(
                    DiscountPolicy.forCorporate(DiscountRate.of(new BigDecimal("0.1000"))),
                    List.of(), null);

            assertThat(invoice.baseAmount()).isEqualTo(Money.yen(new BigDecimal("420000")));
            assertThat(invoice.discountAmount()).isEqualTo(Money.yen(new BigDecimal("42000")));
            assertThat(invoice.taxAmount()).isEqualTo(Money.yen(new BigDecimal("37800")));
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("415800")));
        }

        /** 調整（明細）は合計に効く（決定 6）。 */
        @Test
        @DisplayName("調整の明細が合計に効く")
        void appliesAdjustments() {
            Invoice invoice = issue(DiscountPolicy.none(),
                    List.of(InvoiceLineItem.of("遅延による減額",
                            Money.yen(new BigDecimal("-20000")))),
                    null);

            // 420,000 - 20,000 = 400,000。消費税 40,000。合計 440,000
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("440000")));
        }

        /** キャンセル料は加算される（US30-9）。 */
        @Test
        @DisplayName("キャンセル料が加算される")
        void addsTheCancellationFee() {
            CancellationFee fee = CancellationFee.forStatus(
                    CancelledAtStatus.IN_TRANSIT, Money.yen(new BigDecimal("420000")));
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), fee);

            // 420,000 + 126,000 = 546,000。消費税 54,600。合計 600,600
            assertThat(invoice.cancellationFee()).isEqualTo(fee);
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("600600")));
        }

        /**
         * <strong>発行した時点の社名を残す。</strong>
         *
         * <p>荷主が社名を変えても、<strong>発行済みの請求書の宛名は変わらない</strong>
         * ——出した書面の内容が後から書き換わるのは、決定 4 が禁じていることと同じである。
         * 荷主 ID から毎回引き直すと、そうなる。
         */
        @Test
        @DisplayName("発行した時点の荷主の社名を持つ")
        void keepsTheShipperNameAtIssuing() {
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), null);

            assertThat(invoice.shipperName())
                    .as("社名が荷主 ID で埋まっている。請求書の宛名が読めない")
                    .isEqualTo("丸紅商事株式会社");
        }

        /**
         * <strong>根拠を持ったまま発行する</strong>（決定 1）。
         *
         * <p>金額だけを持つと、あとから「なぜその金額か」を答えられない。
         */
        @Test
        @DisplayName("基本料金の根拠を持つ")
        void keepsTheChargeBasis() {
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), null);

            assertThat(invoice.charge().legCount()).isEqualTo(2);
            assertThat(invoice.charge().weightKg()).isEqualByComparingTo("4200");
            assertThat(invoice.charge().cargoType()).isEqualTo(CargoType.GENERAL);
        }

        /**
         * <strong>割引率を持つ</strong>（22-4）。
         *
         * <p>額だけでは率を復元できない——基本料金と割引額から割り戻すと丸めの分ずれる。
         */
        @Test
        @DisplayName("割引率を持つ")
        void keepsTheDiscountRate() {
            Invoice invoice = issue(
                    DiscountPolicy.forCorporate(DiscountRate.of(new BigDecimal("0.1000"))),
                    List.of(), null);

            assertThat(invoice.discountRate().value()).isEqualByComparingTo("0.1000");
        }

        /** 個人荷主では割引率を持たない（22-3）。**0% ではなく「無い」。** */
        @Test
        @DisplayName("割引が無ければ、割引率も持たない")
        void hasNoDiscountRateWhenNoDiscountApplies() {
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), null);

            assertThat(invoice.discountRate())
                    .as("割引が無いのに率を持っている。0% と契約なしが区別できない")
                    .isNull();
            assertThat(invoice.discountAmount()).isEqualTo(Money.zero());
        }
    }

    @Nested
    @DisplayName("発行後は動かない（決定 4）")
    class Immutability {

        /**
         * <strong>明細を足せない。</strong>
         *
         * <p>請求書は荷主へ出す約束であり、出したあとに黙って変わると請求の根拠が消える。
         * 訂正は US23（IT12）で「取り消して出し直す」形にする。
         */
        @Test
        @DisplayName("発行後に明細を足せない")
        void rejectsAddingLineItemsAfterIssuing() {
            Invoice invoice = issue(DiscountPolicy.none(), List.of(), null);

            InvoiceLineItem afterwards = InvoiceLineItem.of("あとから", Money.yen(BigDecimal.ONE));
            List<InvoiceLineItem> items = invoice.lineItems();

            assertThatThrownBy(() -> items.add(afterwards))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        /**
         * <strong>渡した一覧を書き換えても中身が変わらない。</strong>
         *
         * <p>写して持たないと、呼び出し元が渡したあとの書き換えでこちらの中身が変わる。
         */
        @Test
        @DisplayName("渡した明細の一覧を書き換えても、精算書は変わらない")
        void copiesTheGivenLineItems() {
            java.util.List<InvoiceLineItem> given = new java.util.ArrayList<>();
            given.add(InvoiceLineItem.of("遅延による減額", Money.yen(new BigDecimal("-10000"))));
            Invoice invoice = issue(DiscountPolicy.none(), given, null);
            Money before = invoice.totalAmount();

            given.add(InvoiceLineItem.of("あとから足した", Money.yen(new BigDecimal("-999999"))));

            assertThat(invoice.lineItems()).hasSize(1);
            assertThat(invoice.totalAmount()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("成り立たない発行")
    class InvalidIssuing {

        /**
         * <strong>宛名はラムダの外で組む。</strong>中で組むと、例外を投げたのが
         * 宛名の組み立てか発行かを判別できない（IT11 の割引方針と同じ理由）。
         */
        @Test
        @DisplayName("識別子・予約・荷主が無ければ発行できない")
        void requiresIdentifiers() {
            InvoiceHeader withoutId = new InvoiceHeader(null, BOOKING, SHIPPER, ISSUED_AT);
            InvoiceHeader withoutBooking = new InvoiceHeader(ID, null, SHIPPER, ISSUED_AT);
            InvoiceHeader withoutShipper = new InvoiceHeader(ID, BOOKING, null, ISSUED_AT);

            assertThatThrownBy(() -> Invoice.issue(withoutId, CHARGES, NO_ADJUSTMENTS,
                    BUSINESS_ZONE)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Invoice.issue(withoutBooking, CHARGES, NO_ADJUSTMENTS,
                    BUSINESS_ZONE)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Invoice.issue(withoutShipper, CHARGES, NO_ADJUSTMENTS,
                    BUSINESS_ZONE)).isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>金額の材料は 4 つ揃って渡す</strong>（{@link InvoiceCharges}）。
         *
         * <p>基本料金の根拠が変われば割引額も税額も変わり、キャンセル料は基本料金から
         * 算定される。ばらばらに渡すと、呼び出し側が「どれとどれが揃っていなければ
         * ならないか」を知ることになる。
         */
        @Test
        @DisplayName("根拠・割引方針・税率が無ければ、材料そのものを作れない")
        void requiresTheBasisOfTheAmount() {
            assertThatThrownBy(() -> InvoiceCharges.of(null, NO_DISCOUNT, TAX))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InvoiceCharges.of(CHARGE, null, TAX))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InvoiceCharges.of(CHARGE, NO_DISCOUNT, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("金額の材料が無ければ発行できない")
        void requiresTheCharges() {
            InvoiceHeader header = new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT);

            assertThatThrownBy(() -> Invoice.issue(header, null, NO_ADJUSTMENTS, BUSINESS_ZONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 明細を渡さなくても発行できる（調整が無い請求は普通にある）。 */
        @Test
        @DisplayName("明細を渡さずに発行できる")
        void issuesWithoutLineItems() {
            Invoice invoice = Invoice.issue(new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT),
                    CHARGES, null, BUSINESS_ZONE);

            assertThat(invoice.lineItems()).isEmpty();
        }

        @Test
        @DisplayName("発行日時が無ければ発行できない")
        void requiresAnIssuedAt() {
            InvoiceHeader withoutIssuedAt = new InvoiceHeader(ID, BOOKING, SHIPPER, null);

            assertThatThrownBy(() -> Invoice.issue(withoutIssuedAt, CHARGES, NO_ADJUSTMENTS,
                    BUSINESS_ZONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>根拠の無い調整を断る</strong>（決定 6）。
         *
         * <p>金額だけ残ると、あとから誰も理由を言えない。
         */
        @Test
        @DisplayName("内容の無い調整は断る")
        void rejectsAdjustmentsWithoutDescription() {
            Money reduction = Money.yen(new BigDecimal("-10000"));

            assertThatThrownBy(() -> InvoiceLineItem.of("  ", reduction))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("内容");
            assertThatThrownBy(() -> InvoiceLineItem.of(null, reduction))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restoring {

        /**
         * <strong>復元では検査しない</strong>（新しい不変条件は既存行を壊す）。
         *
         * <p>列が無かったころの行が読めなくなると、過去の請求書が 1 件も開けなくなる。
         * 検査するのは新規に受け入れるとき（{@code issue}）である。
         */
        @Test
        @DisplayName("永続化された行から、状態ごと復元する")
        void restoresFromAPersistedRow() {
            // **保存された金額をそのまま渡す**（決定 4）。係数とは別の値にして、
            // 復元が計算し直していないことを確かめる
            InvoiceAmounts persisted = new InvoiceAmounts(
                    Money.yen(new BigDecimal("111111")), Money.yen(new BigDecimal("11111")),
                    Money.yen(new BigDecimal("10000")), Money.yen(new BigDecimal("110000")));
            Invoice invoice = Invoice.restore(new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT),
                    InvoiceCharges.of(CHARGE, DiscountPolicy.forCorporate(
                            DiscountRate.of(new BigDecimal("0.1000"))), TaxRate.standard()),
                    persisted,
                    List.of(InvoiceLineItem.of("遅延による減額",
                            Money.yen(new BigDecimal("-10000")))),
                    new InvoiceLifecycle(PaymentStatus.CONFIRMED,
                            LocalDate.parse("2027-10-31"), null, null, null));

            assertThat(invoice.paymentStatus())
                    .as("復元で状態が落ちている。入金済の請求書が未入金に戻る")
                    .isEqualTo(PaymentStatus.CONFIRMED);
            assertThat(invoice.lineItems()).hasSize(1);
            assertThat(invoice.invoiceId()).isEqualTo(ID);
            assertThat(invoice.cargoBookingId()).isEqualTo(BOOKING);
            assertThat(invoice.shipperId()).isEqualTo(SHIPPER);
            assertThat(invoice.taxRate().value()).isEqualByComparingTo("0.1000");
            assertThat(invoice.totalAmount())
                    .as("復元で金額を計算し直している。基準運賃を変えると過去の請求書が変わる")
                    .isEqualTo(Money.yen(new BigDecimal("110000")));
            assertThat(invoice.baseAmount()).isEqualTo(Money.yen(new BigDecimal("111111")));
        }

        /** 明細が無い行も復元できる。 */
        @Test
        @DisplayName("明細の無い行も復元できる")
        void restoresARowWithoutLineItems() {
            Invoice invoice = Invoice.restore(new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT),
                    CHARGES, InvoiceAmounts.calculate(CHARGES, java.util.List.of()),
                    null, InvoiceLifecycle.issued(LocalDate.parse("2027-10-31")));

            assertThat(invoice.lineItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("支払いの状態")
    class Payment {

        /**
         * <strong>本 IT で起こす遷移は「発行」の 1 本だけである</strong>（決定 3）。
         *
         * <p>`OVERDUE` / `REFUNDED` へ遷移させる相手は US23 まで現れない。
         * それでも 4 値すべてを扱う場所を回るのは、値を足したときに落とさないためである。
         */
        @Test
        @DisplayName("支払いの状態は、正典と同じ 4 値である")
        void hasTheAgreedValues() {
            assertThat(java.util.Arrays.stream(PaymentStatus.values()).map(Enum::name))
                    .as("支払いの状態が増減した。**遷移させる場所も足すこと**")
                    .containsExactly("PENDING", "CONFIRMED", "OVERDUE", "REFUNDED");
        }

        /** すべての値が表示名を持つ（Try 3 の一般形）。 */
        @Test
        @DisplayName("すべての支払い状態が、日本語の表示名を持つ")
        void everyStatusHasALabel() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(status.label())
                        .as("%s の表示名が無い。画面に英字がそのまま出る", status)
                        .isNotBlank()
                        .doesNotMatch("^[A-Z_]+$");
            }
        }
    }
}
