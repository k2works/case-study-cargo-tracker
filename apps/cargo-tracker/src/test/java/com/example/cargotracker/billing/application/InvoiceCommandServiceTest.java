package com.example.cargotracker.billing.application;

import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommand;
import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.outboundservices.acl.BookingSettlementPort;
import com.example.cargotracker.billing.application.internal.outboundservices.acl.ShipperDiscountPort;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCalculationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceCommandServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ShipperDiscountPort shipperDiscountPort;

    @Mock
    private BookingSettlementPort bookingSettlementPort;

    @InjectMocks
    private InvoiceCommandService invoiceCommandService;

    @Test
    void calculateFreight_請求書が存在する場合に料金を算出して確定できる() {
        Invoice invoice = new Invoice(
                InvoiceId.of("INV-20260401-ABCD1234"),
                "BK-001",
                150_000,
                DiscountPolicy.none(),
                LocalDate.now().plusDays(30)
        );
        when(invoiceRepository.findByBookingId("BK-001")).thenReturn(Optional.of(invoice));

        FreightCalculationResult result = invoiceCommandService.calculateFreight(
                new CalculateFreightCommand("BK-001", -10_000, "破損補償")
        );

        assertEquals("BK-001", result.bookingId());
        assertEquals(150_000, result.baseFreight());
        assertEquals(-10_000, result.adjustmentAmount());
        assertEquals("破損補償", result.adjustmentReason());
        assertEquals(140_000, result.finalAmount());
        verify(invoiceRepository).update(any());
        verify(bookingSettlementPort).settleBooking("BK-001");
    }

    @Test
    void calculateFreight_請求書が存在しない場合は例外が発生する() {
        when(invoiceRepository.findByBookingId("BK-UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> invoiceCommandService.calculateFreight(
                        new CalculateFreightCommand("BK-UNKNOWN", 0, "")
                ));
        verify(invoiceRepository, never()).update(any());
    }
}
