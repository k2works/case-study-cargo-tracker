package com.example.cargotracker.billing.domain.model;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceTest {

    @Test
    void shouldCreateInvoice_withNoneDiscount() {
        Invoice invoice = new Invoice(
                InvoiceId.of("INV-20260401-ABCD1234"),
                "BK-001",
                500_000,
                DiscountPolicy.none(),
                LocalDate.of(2026, 5, 1)
        );

        assertEquals("INV-20260401-ABCD1234", invoice.getInvoiceId().value());
        assertEquals("BK-001", invoice.getBookingId());
        assertEquals(500_000, invoice.getTotalAmountValue());
        assertEquals(500_000, invoice.getDiscountedAmountValue());
        assertEquals(PaymentStatus.PENDING, invoice.getPaymentStatus());
    }

    @Test
    void shouldCreateInvoice_withCorporateDiscount() {
        Invoice invoice = new Invoice(
                InvoiceId.of("INV-20260401-ABCD1234"),
                "BK-001",
                500_000,
                DiscountPolicy.corporate(new BigDecimal("0.10")),
                LocalDate.of(2026, 5, 1)
        );

        assertEquals(500_000, invoice.getTotalAmountValue());
        assertEquals(450_000, invoice.getDiscountedAmountValue());
    }

    @Test
    void confirmPayment_shouldChangeStatusToConfirmed() {
        Invoice invoice = new Invoice(
                InvoiceId.of("INV-20260401-ABCD1234"),
                "BK-001",
                500_000,
                DiscountPolicy.none(),
                LocalDate.of(2026, 5, 1)
        );

        invoice.confirmPayment();

        assertEquals(PaymentStatus.CONFIRMED, invoice.getPaymentStatus());
    }

    @Test
    void confirmPayment_shouldThrow_whenAlreadyConfirmed() {
        Invoice invoice = new Invoice(
                InvoiceId.of("INV-20260401-ABCD1234"),
                "BK-001",
                500_000,
                DiscountPolicy.none(),
                LocalDate.of(2026, 5, 1)
        );
        invoice.confirmPayment();

        assertThrows(IllegalStateException.class, invoice::confirmPayment);
    }

    @Test
    void shouldThrow_whenBookingIdIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new Invoice(
                        InvoiceId.of("INV-20260401-ABCD1234"),
                        "",
                        500_000,
                        DiscountPolicy.none(),
                        LocalDate.of(2026, 5, 1)
                )
        );
    }

    @Test
    void shouldThrow_whenTotalAmountIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new Invoice(
                        InvoiceId.of("INV-20260401-ABCD1234"),
                        "BK-001",
                        -1,
                        DiscountPolicy.none(),
                        LocalDate.of(2026, 5, 1)
                )
        );
    }
}
