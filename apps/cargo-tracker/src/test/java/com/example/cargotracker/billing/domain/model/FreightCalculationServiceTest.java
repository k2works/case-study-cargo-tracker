package com.example.cargotracker.billing.domain.model;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCalculationResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FreightCalculationServiceTest {

    private final FreightCalculationService service = new FreightCalculationService();

    @Test
    void calculate_調整なしの場合は基本料金がそのまま最終料金になる() {
        Invoice invoice = invoice("BK-001", 150_000);

        FreightCalculationResult result = service.calculate(invoice, 0, "");

        assertEquals("BK-001", result.bookingId());
        assertEquals(150_000, result.baseFreight());
        assertEquals(0, result.adjustmentAmount());
        assertEquals("", result.adjustmentReason());
        assertEquals(150_000, result.finalAmount());
    }

    @Test
    void calculate_調整額がある場合は基本料金に調整額を加算した最終料金になる() {
        Invoice invoice = invoice("BK-002", 200_000);

        FreightCalculationResult result = service.calculate(invoice, -20_000, "破損補償");

        assertEquals("BK-002", result.bookingId());
        assertEquals(200_000, result.baseFreight());
        assertEquals(-20_000, result.adjustmentAmount());
        assertEquals("破損補償", result.adjustmentReason());
        assertEquals(180_000, result.finalAmount());
    }

    @Test
    void calculate_加算調整の場合は最終料金が基本料金より増加する() {
        Invoice invoice = invoice("BK-003", 100_000);

        FreightCalculationResult result = service.calculate(invoice, 30_000, "追加荷役費");

        assertEquals(130_000, result.finalAmount());
    }

    private Invoice invoice(String bookingId, int amount) {
        return new Invoice(
                InvoiceId.of("INV-TEST-0001"),
                bookingId,
                amount,
                DiscountPolicy.none(),
                LocalDate.now().plusDays(30)
        );
    }
}
