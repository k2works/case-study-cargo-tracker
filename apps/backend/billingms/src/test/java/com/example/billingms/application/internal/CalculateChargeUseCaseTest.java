package com.example.billingms.application.internal;

import static com.example.billingms.ChargeFixtures.domesticSnapshotLegs;
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
                "GENERAL", "Tokyo", "JP", "Los Angeles", "US", 2, domesticSnapshotLegs(2),
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
                            "Tokyo", "JP", "Singapore", "SG", 1, domesticSnapshotLegs(1),
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
                            "Tokyo", "JP", "Los Angeles", "US", 2, domesticSnapshotLegs(2),
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
                            "Tokyo", "JP", "Los Angeles", "US", 1, domesticSnapshotLegs(1), null, null,
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
                            "Tokyo", "JP", "Los Angeles", "US", 1, domesticSnapshotLegs(1),
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

        /**
         * <strong>経路が決まる前にキャンセルされた予約でも、料金を出せる</strong>
         * （IT11 レビュー 高 1）。
         *
         * <p>仮受付・経路提案中でキャンセルされた予約は<strong>旅程を持たない</strong>
         * （区間数 0）。それでも精算の対象には入る——{@code CancelledAtStatus} が
         * {@code PRELIMINARY} / {@code ROUTE_PROPOSED} に料率 0% を定義しており、
         * <strong>業務として想定されている</strong>。
         *
         * <p>実環境で確かめたところ、この予約は未算出の一覧に並び、開くと
         * <strong>500 が返っていた</strong>——経理担当者が最初に開く画面から到達できる。
         * 運んでいない以上、基本料金もキャンセル料も 0 円である。
         */
        @Test
        @DisplayName("経路が決まる前にキャンセルされた予約は、0 円として算出できる")
        void calculatesZeroForCargoCancelledBeforeRouting() {
            when(snapshots.findBillable("BKG-2026000045")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000045", "CANCELLED", "2", "山田太郎",
                            false, null, new BigDecimal("1000"), "GENERAL",
                            "Tokyo", "JP", "Los Angeles", "US", 0, List.of(), null, null,
                            new BillableCargoSnapshot.Cancellation("PRELIMINARY",
                                    Instant.parse("2027-09-01T00:00:00Z")))));

            ChargeCalculation calculation = useCase.calculate("BKG-2026000045");

            assertThat(calculation.baseAmount())
                    .as("運んでいない貨物に運賃が出ている")
                    .isEqualTo(Money.zero());
            assertThat(calculation.cancellationFee().amount())
                    .as("何も手配していない段階のキャンセルに料金が出ている")
                    .isEqualTo(Money.zero());
            assertThat(calculation.totalAmount()).isEqualTo(Money.zero());
        }

        /**
         * <strong>運んだ貨物の旅程が無いのは、こちらの不備である。</strong>
         *
         * <p>引取が終わっているのに区間が 1 本も無いのは、データが壊れている——
         * 0 円で通すと<strong>運んだのに請求しない</strong>ことになる。
         * キャンセル（運んでいない）とは区別する。
         */
        @Test
        @DisplayName("引取済なのに旅程が無い予約は断る")
        void rejectsDeliveredCargoWithoutAnyLeg() {
            when(snapshots.findBillable("BKG-2026000046")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000046", "DELIVERED", "1", "丸紅商事",
                            true, new BigDecimal("0.1000"), new BigDecimal("4200"), "GENERAL",
                            "Tokyo", "JP", "Los Angeles", "US", 0, domesticSnapshotLegs(0),
                            Instant.parse("2027-09-26T00:00:00Z"), null, null)));

            assertThatThrownBy(() -> useCase.calculate("BKG-2026000046"))
                    .as("運んだ貨物の旅程が無いのに、黙って 0 円で通している")
                    .isInstanceOf(BillingNotAvailableException.class)
                    .hasMessageContaining("旅程");
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
            // 420,000 - 42,000 - 20,000 = 358,000。
            // **東京 → ロサンゼルスは輸出免税**（[ADR-027] 決定 8 の改訂）なので合計は同額
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("358000")));
            assertThat(invoice.taxRate().exempted())
                    .as("国際輸送に消費税が付いている")
                    .isTrue();
        }

        /**
         * <strong>国内輸送には消費税が付く</strong>（決定 8 の改訂と対で見る）。
         *
         * <p>免税だけを見ると、常に 0% を返す実装でも緑になる。
         */
        @Test
        @DisplayName("国内輸送の請求には消費税が付く")
        void chargesTaxForDomesticTransport() {
            when(snapshots.findBillable("BKG-2026000011")).thenReturn(Optional.of(
                    new BillableCargoSnapshot("BKG-2026000011", "DELIVERED", "1", "丸紅商事",
                            false, null, new BigDecimal("1000"), "GENERAL",
                            "Tokyo", "JP", "Osaka", "JP", 1, domesticSnapshotLegs(1),
                            Instant.parse("2027-09-26T00:00:00Z"), null, null)));
            when(numbering.next()).thenReturn(InvoiceId.of("INV-2026000002"));

            Invoice invoice = useCase.confirm("BKG-2026000011", List.of());

            // 50,000 × 1 区間（国内 1.0）× 1.0 × 1.0 = 50,000。消費税 5,000
            assertThat(invoice.taxRate().exempted())
                    .as("国内輸送が免税になっている。取るべき消費税を取っていない")
                    .isFalse();
            assertThat(invoice.totalAmount()).isEqualTo(Money.yen(new BigDecimal("55000")));
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

            List<AdjustmentCommand> withoutDescription =
                    List.of(new AdjustmentCommand("  ", new BigDecimal("-20000")));

            assertThatThrownBy(() -> useCase.confirm("BKG-2026000007", withoutDescription))
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
