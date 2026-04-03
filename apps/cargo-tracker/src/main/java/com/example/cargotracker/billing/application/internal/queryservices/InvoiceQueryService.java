package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public List<InvoiceSummary> findAll() {
        return invoiceRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<InvoiceSummary> findById(String id) {
        return invoiceRepository.findById(InvoiceId.of(id)).map(this::toSummary);
    }

    private InvoiceSummary toSummary(Invoice invoice) {
        return new InvoiceSummary(
                invoice.getId().value().toString(),
                invoice.getBookingId(),
                invoice.getFreightChargeId(),
                invoice.getAmount(),
                invoice.getDueDate(),
                resolvePaymentStatus(invoice)
        );
    }

    public boolean hasOverdueInvoices() {
        return invoiceRepository.findAll().stream().anyMatch(this::isOverdue);
    }

    private String resolvePaymentStatus(Invoice invoice) {
        return isOverdue(invoice) ? "支払い期限超過" : invoice.getPaymentStatus().getDisplayName();
    }

    private boolean isOverdue(Invoice invoice) {
        return invoice.getPaymentStatus().name().equals("PENDING")
                && invoice.getDueDate().isBefore(LocalDate.now());
    }

    public record InvoiceSummary(
            String id,
            String bookingId,
            String freightChargeId,
            BigDecimal amount,
            LocalDate dueDate,
            String paymentStatus
    ) {}
}
