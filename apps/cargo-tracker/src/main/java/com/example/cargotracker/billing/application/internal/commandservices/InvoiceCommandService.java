package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.acl.BookingSettlementPort;
import com.example.cargotracker.billing.application.internal.outboundservices.acl.ShipperDiscountPort;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCalculationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class InvoiceCommandService {

    private final InvoiceRepository invoiceRepository;
    private final ShipperDiscountPort shipperDiscountPort;
    private final BookingSettlementPort bookingSettlementPort;

    public InvoiceCommandService(
            InvoiceRepository invoiceRepository,
            ShipperDiscountPort shipperDiscountPort,
            BookingSettlementPort bookingSettlementPort
    ) {
        this.invoiceRepository = invoiceRepository;
        this.shipperDiscountPort = shipperDiscountPort;
        this.bookingSettlementPort = bookingSettlementPort;
    }

    public InvoiceId generateInvoice(GenerateInvoiceCommand command) {
        DiscountPolicy discountPolicy = shipperDiscountPort.getDiscountPolicyForShipper(command.shipperId());
        InvoiceId invoiceId = InvoiceId.generate();
        LocalDate dueDate = LocalDate.now().plusDays(30);
        Invoice invoice = new Invoice(
                invoiceId,
                command.bookingId(),
                command.baseAmountValue(),
                discountPolicy,
                dueDate
        );
        invoiceRepository.save(invoice);
        return invoiceId;
    }

    public void confirmPayment(ConfirmPaymentCommand command) {
        Invoice invoice = invoiceRepository.findByInvoiceId(InvoiceId.of(command.invoiceId()))
                .orElseThrow(() -> new IllegalArgumentException("請求書が見つかりません: " + command.invoiceId()));
        invoice.confirmPayment();
        invoiceRepository.update(invoice);
        bookingSettlementPort.settleBooking(invoice.getBookingId());
    }

    public FreightCalculationResult calculateFreight(CalculateFreightCommand command) {
        Invoice invoice = invoiceRepository.findByBookingId(command.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("請求書が見つかりません: " + command.bookingId()));
        FreightCalculationService freightService = new FreightCalculationService();
        FreightCalculationResult result = freightService.calculate(invoice, command.adjustmentAmount(), command.adjustmentReason());
        invoice.confirmPayment();
        invoiceRepository.update(invoice);
        bookingSettlementPort.settleBooking(invoice.getBookingId());
        return result;
    }
}
