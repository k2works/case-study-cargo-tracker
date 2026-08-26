package com.example.billingms.application.internal;

import static com.example.billingms.ChargeFixtures.domesticLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.billingms.application.port.BookingSettlementNotifier;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.BillingBookingId;
import com.example.billingms.domain.model.BillingShipperId;
import com.example.billingms.domain.model.CargoType;
import com.example.billingms.domain.model.DiscountPolicy;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.InvoiceCharges;
import com.example.billingms.domain.model.InvoiceId;
import com.example.billingms.domain.model.PaymentStatus;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 精算の処理（US23・[ADR-028]）。
 *
 * <p><strong>入金の確認は手作業である</strong>（受入基準 23-3 の代替）。決済機関との
 * 連携先が無い。だからこそ、入れた根拠が残ることと、<strong>予約まで閉じること</strong>を
 * ここで固定する。
 */
@DisplayName("精算の処理")
class SettleInvoiceUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** 「今日」は 2027-12-01。発行日（2027-10-01）+ 30 日の期限をとうに過ぎている。 */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2027-12-01T00:00:00Z"), ZONE);

    private InvoiceRepository invoices;

    private BookingSettlementNotifier bookings;

    private SettleInvoiceUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class);
        bookings = mock(BookingSettlementNotifier.class);
        useCase = new SettleInvoiceUseCase(invoices, bookings, CLOCK);
    }

    private static Invoice issued() {
        return Invoice.issue(InvoiceId.of("INV-2026000001"),
                BillingBookingId.of("BKG-2026000007"),
                BillingShipperId.corporate("1", "丸紅商事株式会社"),
                InvoiceCharges.of(
                        TransportCharge.of(domesticLegs(2), new BigDecimal("4200"),
                                CargoType.GENERAL),
                        DiscountPolicy.none(), TaxRate.standard()),
                List.of(), Instant.parse("2027-10-01T00:00:00Z"), ZONE);
    }

    private static PaymentCommand command() {
        return new PaymentCommand(new BigDecimal("462000"), LocalDate.parse("2027-10-15"),
                "BANK_TRANSFER", "FT27101500123");
    }

    @Nested
    @DisplayName("入金の確認")
    class ConfirmingPayment {

        @Test
        @DisplayName("入金を確認し、予約を精算済にする")
        void confirmsAndClosesTheBooking() {
            when(invoices.findById("INV-2026000001")).thenReturn(Optional.of(issued()));

            Invoice confirmed = useCase.confirmPayment("INV-2026000001", command());

            assertThat(confirmed.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
            verify(invoices).confirmPayment(confirmed);
            // **予約まで閉じて初めて完了である**（受入基準 23-4）
            verify(bookings).markSettled("BKG-2026000007");
        }

        /**
         * <strong>断られたら予約には知らせない。</strong>
         *
         * <p>知らせてしまうと、請求は未入金のままなのに予約だけが精算済になる。
         */
        @Test
        @DisplayName("すでに入金済なら、予約には知らせない")
        void doesNotNotifyWhenTheInvoiceRejectsThePayment() {
            when(invoices.findById("INV-2026000001"))
                    .thenReturn(Optional.of(issued().confirmPayment(
                            com.example.billingms.domain.model.Payment.of(
                                    com.example.billingms.domain.model.Money.yen(
                                            new BigDecimal("462000")),
                                    LocalDate.parse("2027-10-15"),
                                    com.example.billingms.domain.model.PaymentMethod
                                            .BANK_TRANSFER, null))));

            assertThatThrownBy(() -> useCase.confirmPayment("INV-2026000001", command()))
                    .isInstanceOf(IllegalStateException.class);

            verify(bookings, never()).markSettled(anyString());
            verify(invoices, never()).confirmPayment(any());
        }

        @Test
        @DisplayName("知らない請求書は断る")
        void rejectsUnknownInvoices() {
            when(invoices.findById("INV-9999999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.confirmPayment("INV-9999999999", command()))
                    .isInstanceOf(InvoiceNotFoundException.class);
        }

        /** <strong>知らない入金方法は断る。</strong>既定値に倒すと、記録がすべて銀行振込になる。 */
        @Test
        @DisplayName("扱いを決めていない入金の方法は断る")
        void rejectsUnknownPaymentMethods() {
            when(invoices.findById("INV-2026000001")).thenReturn(Optional.of(issued()));

            assertThatThrownBy(() -> useCase.confirmPayment("INV-2026000001",
                    new PaymentCommand(new BigDecimal("462000"),
                            LocalDate.parse("2027-10-15"), "CRYPTO", null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("取り消し（赤伝）")
    class Revoking {

        @Test
        @DisplayName("理由とともに取り消し、記録を残す")
        void revokesWithAReason() {
            when(invoices.findById("INV-2026000001")).thenReturn(Optional.of(issued()));

            Invoice revoked = useCase.revoke("INV-2026000001", "金額の誤りのため");

            assertThat(revoked.voided()).isTrue();
            assertThat(revoked.voidReason()).isEqualTo("金額の誤りのため");
            assertThat(revoked.voidedAt()).isEqualTo(CLOCK.instant());
            verify(invoices).revoke(revoked);
        }

        @Test
        @DisplayName("理由が無ければ書き込まない")
        void doesNotWriteWithoutAReason() {
            when(invoices.findById("INV-2026000001")).thenReturn(Optional.of(issued()));

            assertThatThrownBy(() -> useCase.revoke("INV-2026000001", " "))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(invoices, never()).revoke(any());
        }
    }

    @Nested
    @DisplayName("支払期限の超過")
    class Overdue {

        /**
         * <strong>業務の暦で「今日」を決める</strong>（[ADR-028] 決定 5）。
         *
         * <p>列に書いて溜めないため、ここで判定しなければ<strong>期限超過は
         * 常に 0 件</strong>になる。
         */
        @Test
        @DisplayName("期限を過ぎた未入金の請求書だけを並べる")
        void listsOnlyOverdueUnpaidInvoices() {
            Invoice unpaid = issued();
            Invoice paid = issued().confirmPayment(
                    com.example.billingms.domain.model.Payment.of(
                            com.example.billingms.domain.model.Money.yen(
                                    new BigDecimal("462000")),
                            LocalDate.parse("2027-10-15"),
                            com.example.billingms.domain.model.PaymentMethod.BANK_TRANSFER,
                            null));
            Invoice revoked = issued().revoke("金額の誤りのため", CLOCK.instant());
            when(invoices.findAll()).thenReturn(List.of(unpaid, paid, revoked));

            assertThat(useCase.overdue())
                    .as("払った請求書や取り消した請求書まで催促することになる")
                    .containsExactly(unpaid);
        }
    }
}
