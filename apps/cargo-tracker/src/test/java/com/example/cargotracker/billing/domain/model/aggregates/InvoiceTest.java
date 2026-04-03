package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Invoice")
class InvoiceTest {

    private static final InvoiceId INVOICE_ID = InvoiceId.generate();
    private static final String BOOKING_ID = "booking-001";
    private static final String FREIGHT_CHARGE_ID = "freight-001";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");
    private static final LocalDate DUE_DATE = LocalDate.now().plusDays(30);

    @Test
    @DisplayName("精算書を生成するとPENDING状態になる")
    void generate_精算書を生成するとPENDING状態になる() {
        Invoice invoice = Invoice.generate(INVOICE_ID, BOOKING_ID, FREIGHT_CHARGE_ID, AMOUNT, DUE_DATE);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(invoice.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(invoice.getFreightChargeId()).isEqualTo(FREIGHT_CHARGE_ID);
        assertThat(invoice.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("PENDING状態の精算書を支払い確認するとCONFIRMED状態になる")
    void confirmPayment_PENDING状態の精算書を支払い確認するとCONFIRMED状態になる() {
        Invoice invoice = Invoice.generate(INVOICE_ID, BOOKING_ID, FREIGHT_CHARGE_ID, AMOUNT, DUE_DATE);
        invoice.confirmPayment();
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED状態の精算書を支払い確認しようとするとIllegalStateExceptionをスローする")
    void confirmPayment_CONFIRMED状態はIllegalStateExceptionをスローする() {
        Invoice invoice = Invoice.generate(INVOICE_ID, BOOKING_ID, FREIGHT_CHARGE_ID, AMOUNT, DUE_DATE);
        invoice.confirmPayment();
        assertThatThrownBy(invoice::confirmPayment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("金額がnullの場合はIllegalArgumentExceptionをスローする")
    void generate_金額がnullの場合はIllegalArgumentExceptionをスローする() {
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, BOOKING_ID, FREIGHT_CHARGE_ID, null, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("金額が0以下の場合はIllegalArgumentExceptionをスローする")
    void generate_金額が0以下の場合はIllegalArgumentExceptionをスローする() {
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, BOOKING_ID, FREIGHT_CHARGE_ID, BigDecimal.ZERO, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("予約IDがnullまたは空の場合はIllegalArgumentExceptionをスローする")
    void generate_予約IDがnullまたは空の場合はIllegalArgumentExceptionをスローする() {
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, null, FREIGHT_CHARGE_ID, AMOUNT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, "", FREIGHT_CHARGE_ID, AMOUNT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("輸送料金IDがnullまたは空の場合はIllegalArgumentExceptionをスローする")
    void generate_輸送料金IDがnullまたは空の場合はIllegalArgumentExceptionをスローする() {
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, BOOKING_ID, null, AMOUNT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Invoice.generate(INVOICE_ID, BOOKING_ID, "", AMOUNT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
