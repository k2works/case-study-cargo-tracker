package com.example.billingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.billingms.application.port.BillingSnapshotFinder;
import com.example.billingms.application.port.InvoiceNumbering;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.CancelledAtStatus;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.InvoiceId;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 料金の算出と確定（US21・US22・[ADR-027]）。
 *
 * <p><strong>起点は経理担当者の操作である</strong>（決定 5）。{@code CargoDeliveredEvent} を
 * 待たない——読む側の無い配線を先に敷かない（[ADR-025] 決定 3 と同じ判断）。
 */
@DisplayName("料金の算出と確定")
class CalculateChargeUseCaseTest {

    private static final Instant NOW = Instant.parse("2027-10-01T00:00:00Z");

    private final BillingSnapshotFinder snapshots = mock(BillingSnapshotFinder.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final InvoiceNumbering numbering = mock(InvoiceNumbering.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"));

    private final CalculateChargeUseCase useCase =
            new CalculateChargeUseCase(snapshots, invoices, numbering, clock);

    /** 法人・2 区間・4,200kg・一般貨物。50,000 × 2 × 4.2 × 1.0 = 420,000 円。 */
    private static BillableCargoSnapshot corporate() {
        return new BillableCargoSnapshot("BKG-2026000007", "DELIVERED", "1",
                "丸紅商事株式会社", true, new BigDecimal("0.1000"), new BigDecimal("4200"),
                "GENERAL", "Tokyo", "Los Angeles", 2,
                Instant.parse("2027-09-26T00:00:00Z"), null, null);
    }

    @Nested
    @DisplayName("算出（保存しない）")
    class Calculating {

        /**
         * <strong>算出しただけでは精算書を作らない</strong>（決定 3）。
         *
         * <p>下書きを持つと、下書きのまま忘れられた精算書が溜まる——それを見つける手段を
         * また作ることになる。
         */
        @Test
        @DisplayName("算出しても精算書は保存されない")
        void doesNotPersistWhileCalculating() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000007");

            assertThat(calculation.baseAmount()).isEqualTo(Money.yen(new BigDecimal("420000")));
            verify(invoices, never()).save(any());
        }

        /** 法人には契約割引が入る（22-1・22-2）。 */
        @Test
        @DisplayName("法人には契約割引が入る")
        void appliesTheContractDiscount() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000007");

            assertThat(calculation.discountRate().value()).isEqualByComparingTo("0.1000");
            assertThat(calculation.discountAmount()).isEqualTo(Money.yen(new BigDecimal("42000")));
        }

        /**
         * <strong>個人には割引が無い</strong>（22-3）。
         *
         * <p>0% ではなく「無い」——0% を出すと、契約が無いことと区別できない。
         */
        @Test
        @DisplayName("個人には割引が無い")
        void appliesNoDiscountToIndividuals() {
            when(snapshots.findBillable("BKG-2026000008")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000008", "DELIVERED", "2", "山田太郎",
                            false, null, new BigDecimal("800"), "REFRIGERATED",
                            "Tokyo", "Singapore", 1,
                            Instant.parse("2027-09-20T00:00:00Z"), null, null)));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000008");

            assertThat(calculation.discountRate())
                    .as("個人に割引率が入っている。契約が無いのに割引の話が始まる")
                    .isNull();
            assertThat(calculation.discountAmount()).isEqualTo(Money.zero());
        }

        /**
         * <strong>法人でも割引率が未設定なら割引は無い</strong>（[ADR-012]）。
         *
         * <p>0% として扱うと、設定し忘れと「割引しない契約」が同じに見える。
         */
        @Test
        @DisplayName("法人でも割引率が未設定なら割引は無い")
        void appliesNoDiscountWhenTheRateIsUnset() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000007", "DELIVERED", "1", "丸紅商事",
                            true, null, new BigDecimal("4200"), "GENERAL",
                            "Tokyo", "Los Angeles", 2,
                            Instant.parse("2027-09-26T00:00:00Z"), null, null)));

            assertThat(useCase.calculate("BKG-2026000007").discountRate()).isNull();
        }

        /** キャンセル料は申請時の状態で算定される（US30-9）。 */
        @Test
        @DisplayName("キャンセルされた予約ではキャンセル料が算定される")
        void calculatesTheCancellationFee() {
            when(snapshots.findBillable("BKG-2026000010")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000010", "CANCELLED", "1", "丸紅商事",
                            true, new BigDecimal("0.1000"), new BigDecimal("1500"), "GENERAL",
                            "Tokyo", "Los Angeles", 1, null, null,
                            new BillableCargoSnapshot.Cancellation("IN_TRANSIT",
                                    Instant.parse("2027-09-10T00:00:00Z")))));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000010");

            assertThat(calculation.cancellationFee()).isNotNull();
            assertThat(calculation.cancellationFee().bookingStatusAtCancel())
                    .isEqualTo(CancelledAtStatus.IN_TRANSIT);
        }

        /**
         * <strong>誤配の記録を根拠として渡す</strong>（21-6）。
         *
         * <p>金額は自動で決めない（決定 6）——どれだけ減額するかは荷主との関係で決まる。
         */
        @Test
        @DisplayName("誤配の記録を根拠として渡すが、金額は決めない")
        void carriesTheMisrouteWithoutDecidingTheAdjustment() {
            when(snapshots.findBillable("BKG-2026000009")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000009", "DELIVERED", "1", "丸紅商事",
                            true, new BigDecimal("0.1000"), new BigDecimal("2500"), "GENERAL",
                            "Tokyo", "Los Angeles", 1,
                            Instant.parse("2027-10-02T00:00:00Z"),
                            new BillableCargoSnapshot.Misroute(
                                    Instant.parse("2027-09-09T00:00:00Z"), "SGSIN", "Singapore"),
                            null)));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000009");

            assertThat(calculation.misroute()).isNotNull();
            assertThat(calculation.misroute().locationName()).isEqualTo("Singapore");
            assertThat(calculation.baseAmount())
                    .as("誤配を理由に金額を勝手に減らしている。判断は経理担当者が行う")
                    .isEqualTo(Money.yen(new BigDecimal("125000")));
        }

        /** 料金算出の対象でなければ断る（決定 5）。 */
        @Test
        @DisplayName("料金算出の対象でない予約は断る")
        void rejectsCargoThatCannotBeBilled() {
            when(snapshots.findBillable("BKG-2026000001")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.calculate("BKG-2026000001"))
                    .isInstanceOf(BillingNotAvailableException.class);
        }

        /** すでに発行済みなら算出させない（決定 4）。**二重請求を防ぐ。** */
        @Test
        @DisplayName("すでに精算書が発行されている予約は断る")
        void rejectsCargoThatIsAlreadyInvoiced() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));
            when(invoices.existsForBooking("BKG-2026000007")).thenReturn(true);

            assertThatThrownBy(() -> useCase.calculate("BKG-2026000007"))
                    .isInstanceOf(AlreadyInvoicedException.class);
        }
    }

    @Nested
    @DisplayName("確定（発行）")
    class Confirming {

        @Test
        @DisplayName("確定すると、未入金の精算書が発行される")
        void issuesAPendingInvoice() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));
            when(numbering.next()).thenReturn(InvoiceId.of("INV-2026000001"));

            Invoice invoice = useCase.confirm("BKG-2026000007", List.of());

            assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(invoice.invoiceId().value()).isEqualTo("INV-2026000001");
            assertThat(invoice.issuedAt()).isEqualTo(NOW);
            verify(invoices).save(invoice);
        }

        /** 調整はここでまとめて受ける（決定 3）。 */
        @Test
        @DisplayName("調整の明細を受け取って合計に反映する")
        void appliesTheGivenAdjustments() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));
            when(numbering.next()).thenReturn(InvoiceId.of("INV-2026000001"));

            Invoice invoice = useCase.confirm("BKG-2026000007",
                    List.of(new AdjustmentCommand("遅延による減額", new BigDecimal("-20000"))));

            assertThat(invoice.lineItems()).hasSize(1);
            // 420,000 - 42,000 - 20,000 = 358,000。消費税 35,800。合計 393,800
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("393800")));
        }

        /**
         * <strong>二重請求を断る</strong>（決定 4）。
         *
         * <p>画面が押させないだけでは守れない——同時に 2 回押されることがある。
         */
        @Test
        @DisplayName("すでに発行されている予約は確定できない")
        void rejectsDoubleInvoicing() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));
            when(invoices.existsForBooking("BKG-2026000007")).thenReturn(true);

            assertThatThrownBy(() -> useCase.confirm("BKG-2026000007", List.of()))
                    .isInstanceOf(AlreadyInvoicedException.class);
            verify(invoices, never()).save(any());
        }

        /** 根拠の無い調整は断る（決定 6）。 */
        @Test
        @DisplayName("内容の無い調整は断る")
        void rejectsAdjustmentsWithoutDescription() {
            when(snapshots.findBillable("BKG-2026000007")).thenReturn(Optional.of(corporate()));
            when(numbering.next()).thenReturn(InvoiceId.of("INV-2026000001"));

            assertThatThrownBy(() -> useCase.confirm("BKG-2026000007",
                    List.of(new AdjustmentCommand("  ", new BigDecimal("-20000")))))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(invoices, never()).save(any());
        }

        /** 対象でない予約は確定できない（決定 5）。 */
        @Test
        @DisplayName("料金算出の対象でない予約は確定できない")
        void rejectsCargoThatCannotBeBilled() {
            when(snapshots.findBillable("BKG-2026000001")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.confirm("BKG-2026000001", List.of()))
                    .isInstanceOf(BillingNotAvailableException.class);
            verify(invoices, never()).save(any());
        }
    }
}
