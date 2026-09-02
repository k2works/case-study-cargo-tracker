package com.example.billingms.domain.model.aggregates;

import static com.example.billingms.ChargeFixtures.domesticLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.billingms.domain.model.valueobjects.BillingBookingId;
import com.example.billingms.domain.model.valueobjects.BillingShipperId;
import com.example.billingms.domain.model.valueobjects.CargoType;
import com.example.billingms.domain.model.valueobjects.DiscountPolicy;
import com.example.billingms.domain.model.valueobjects.InvoiceCharges;
import com.example.billingms.domain.model.valueobjects.InvoiceHeader;
import com.example.billingms.domain.model.valueobjects.InvoiceId;
import com.example.billingms.domain.model.valueobjects.Payment;
import com.example.billingms.domain.model.valueobjects.PaymentMethod;
import com.example.billingms.domain.model.valueobjects.PaymentStatus;
import com.example.billingms.domain.model.valueobjects.TaxRate;
import com.example.billingms.domain.model.valueobjects.TransportCharge;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("精算書のライフサイクル")
class InvoiceLifecycleTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");
    private static final InvoiceId ID = InvoiceId.of("INV-2026000001");
    private static final BillingBookingId BOOKING = BillingBookingId.of("BKG-2026000007");
    private static final BillingShipperId SHIPPER =
            BillingShipperId.corporate("1", "丸紅商事株式会社");
    private static final Instant ISSUED_AT = Instant.parse("2027-10-01T00:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2027-10-05T00:00:00Z");
    private static final TransportCharge CHARGE =
            TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL);
    private static final InvoiceCharges CHARGES =
            InvoiceCharges.of(CHARGE, DiscountPolicy.none(), TaxRate.standard());

    private static Invoice invoice() {
        return Invoice.issue(new InvoiceHeader(ID, BOOKING, SHIPPER, ISSUED_AT), CHARGES,
                List.of(), BUSINESS_ZONE);
    }

    private static Payment paymentOf(Invoice invoice) {
        return Payment.of(invoice.totalAmount(), LocalDate.parse("2027-10-10"),
                PaymentMethod.BANK_TRANSFER, "RCPT-1");
    }

    @Nested
    @DisplayName("入金確認")
    class PaymentConfirmation {

        @Test
        @DisplayName("入金を確認すると支払い状態と入金記録を持つ")
        void confirmsPayment() {
            Invoice invoice = invoice();
            Payment confirmed = paymentOf(invoice);

            Invoice paid = invoice.confirmPayment(confirmed);

            assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
            assertThat(paid.payment()).isEqualTo(confirmed);
            assertThat(paid.totalAmount()).isEqualTo(invoice.totalAmount());
        }

        @Test
        @DisplayName("入金記録なしでは確認できない")
        void rejectsMissingPayment() {
            Invoice invoice = invoice();

            assertThatThrownBy(() -> invoice.confirmPayment(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ請求書の入金は二度確認できない")
        void rejectsDuplicatePayment() {
            Invoice invoice = invoice();
            Payment confirmed = paymentOf(invoice);

            Invoice paid = invoice.confirmPayment(confirmed);

            assertThatThrownBy(() -> paid.confirmPayment(confirmed))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(ID.value());
        }
    }

    @Nested
    @DisplayName("取り消し")
    class Revoking {

        @Test
        @DisplayName("取り消すと理由と日時を持つ")
        void revokesAnInvoice() {
            Invoice voided = invoice().revoke("二重発行", REVOKED_AT);

            assertThat(voided.voided()).isTrue();
            assertThat(voided.voidedAt()).isEqualTo(REVOKED_AT);
            assertThat(voided.voidReason()).isEqualTo("二重発行");
        }

        @Test
        @DisplayName("理由や日時なしでは取り消せない")
        void requiresReasonAndTimestamp() {
            Invoice invoice = invoice();

            assertThatThrownBy(() -> invoice.revoke(" ", REVOKED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> invoice.revoke("二重発行", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("取り消した請求書は再取り消しも入金確認もできない")
        void rejectsOperationsAfterRevoked() {
            Invoice voided = invoice().revoke("二重発行", REVOKED_AT);
            Payment confirmed = paymentOf(voided);

            assertThatThrownBy(() -> voided.revoke("再取消", REVOKED_AT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> voided.confirmPayment(confirmed))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("入金済みの請求書は取り消せない")
        void rejectsRevokingPaidInvoice() {
            Invoice invoice = invoice();
            Invoice paid = invoice.confirmPayment(paymentOf(invoice));

            assertThatThrownBy(() -> paid.revoke("二重発行", REVOKED_AT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("支払期限")
    class Overdue {

        @Test
        @DisplayName("期限日の翌日から期限超過になる")
        void becomesOverdueAfterDueDate() {
            Invoice invoice = invoice();

            assertThat(invoice.overdue(invoice.dueDate())).isFalse();
            assertThat(invoice.overdue(invoice.dueDate().plusDays(1))).isTrue();
        }

        @Test
        @DisplayName("基準日が無ければ判断できない")
        void requiresToday() {
            Invoice invoice = invoice();

            assertThatThrownBy(() -> invoice.overdue(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("取り消し済みや入金済みは期限超過にしない")
        void ignoresClosedInvoices() {
            Invoice invoice = invoice();
            LocalDate afterDueDate = invoice.dueDate().plusDays(1);

            assertThat(invoice.revoke("二重発行", REVOKED_AT).overdue(afterDueDate)).isFalse();
            assertThat(invoice.confirmPayment(paymentOf(invoice)).overdue(afterDueDate)).isFalse();
        }
    }
}
